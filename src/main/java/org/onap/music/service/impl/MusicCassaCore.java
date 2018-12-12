/*
 * ============LICENSE_START==========================================
 * org.onap.music
 * ===================================================================
 *  Copyright (c) 2017 AT&T Intellectual Property
 * ===================================================================
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * 
 * ============LICENSE_END=============================================
 * ====================================================================
 */
package org.onap.music.service.impl;


import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.UUID;

import org.onap.music.datastore.MusicDataStore;
import org.onap.music.datastore.MusicDataStoreHandle;
import org.onap.music.datastore.Condition;
import org.onap.music.datastore.PreparedQueryObject;
import org.onap.music.eelf.logging.EELFLoggerDelegate;
import org.onap.music.eelf.logging.format.AppMessages;
import org.onap.music.eelf.logging.format.ErrorSeverity;
import org.onap.music.eelf.logging.format.ErrorTypes;
import org.onap.music.exceptions.MusicLockingException;
import org.onap.music.exceptions.MusicQueryException;
import org.onap.music.exceptions.MusicServiceException;
import org.onap.music.lockingservice.cassandra.CassaLockStore;
import org.onap.music.lockingservice.cassandra.MusicLockState;
import org.onap.music.lockingservice.cassandra.CassaLockStore.LockObject;
import org.onap.music.main.MusicUtil;
import org.onap.music.main.ResultType;
import org.onap.music.main.ReturnType;
import org.onap.music.service.MusicCoreService;

import com.datastax.driver.core.ColumnDefinitions;
import com.datastax.driver.core.ColumnDefinitions.Definition;
import com.datastax.driver.core.DataType;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.TableMetadata;


/**
 * This class .....
 * 
 *
 */
public class MusicCassaCore implements MusicCoreService {

    public static CassaLockStore mLockHandle = null;;
    private static EELFLoggerDelegate logger = EELFLoggerDelegate.getLogger(MusicCassaCore.class);
    private static boolean unitTestRun=true;
    private static MusicCassaCore musicCassaCoreInstance = null;
    
    private MusicCassaCore() {
    	
    }
    public static MusicCassaCore getInstance() {
    	
    	if(musicCassaCoreInstance == null) {
    		musicCassaCoreInstance = new MusicCassaCore();
    	}
    	return musicCassaCoreInstance;
    }
    
    public static CassaLockStore getLockingServiceHandle() throws MusicLockingException {
        logger.info(EELFLoggerDelegate.applicationLogger,"Acquiring lock store handle");
        long start = System.currentTimeMillis();

        if (mLockHandle == null) {
            try {
                mLockHandle = new CassaLockStore(MusicDataStoreHandle.getDSHandle());
            } catch (Exception e) {
            	logger.error(EELFLoggerDelegate.errorLogger,e.getMessage(), AppMessages.LOCKHANDLE,ErrorSeverity.CRITICAL, ErrorTypes.LOCKINGERROR);
                throw new MusicLockingException("Failed to aquire Locl store handle " + e);
            }
        }
        long end = System.currentTimeMillis();
        logger.info(EELFLoggerDelegate.applicationLogger,"Time taken to acquire lock store handle:" + (end - start) + " ms");
        return mLockHandle;
    }


    public  String createLockReference(String fullyQualifiedKey) {
        return createLockReference(fullyQualifiedKey, true);
    }

    public  String createLockReference(String fullyQualifiedKey, boolean isWriteLock) {
        String[] splitString = fullyQualifiedKey.split("\\.");
        String keyspace = splitString[0];
        String table = splitString[1];
        String lockName = splitString[2];

        logger.info(EELFLoggerDelegate.applicationLogger,"Creating lock reference for lock name:" + lockName);
        long start = System.currentTimeMillis();
        String lockReference = null;
        try {
			lockReference = "" + getLockingServiceHandle().genLockRefandEnQueue(keyspace, table, lockName, isWriteLock);
		} catch (MusicLockingException | MusicServiceException | MusicQueryException e) {
			e.printStackTrace();
		}
        long end = System.currentTimeMillis();
        logger.info(EELFLoggerDelegate.applicationLogger,"Time taken to create lock reference:" + (end - start) + " ms");
        return lockReference;
    }


    public  ReturnType acquireLockWithLease(String fullyQualifiedKey, String lockReference, long leasePeriod) throws MusicLockingException, MusicQueryException, MusicServiceException  {
     	evictExpiredLockHolder(fullyQualifiedKey,leasePeriod);
    		return acquireLock(fullyQualifiedKey, lockReference);
    }

    private  void evictExpiredLockHolder(String fullyQualifiedKey, long leasePeriod) throws MusicLockingException, MusicQueryException, MusicServiceException {

        String[] splitString = fullyQualifiedKey.split("\\.");
        String keyspace = splitString[0];
        String table = splitString[1];
        String primaryKeyValue = splitString[2];

		LockObject currentLockHolderObject = getLockingServiceHandle().peekLockQueue(keyspace, table, primaryKeyValue);
		
		/* Release the lock of the previous holder if it has expired. if the update to the acquire time has not reached due to network delays, simply use the create time as the 
		 * reference*/
		
		long referenceTime = Math.max(Long.parseLong(currentLockHolderObject.acquireTime), Long.parseLong(currentLockHolderObject.createTime));
		if((System.currentTimeMillis() - referenceTime) > leasePeriod) {
			forciblyReleaseLock(fullyQualifiedKey,  currentLockHolderObject.lockRef+"");
            logger.info(EELFLoggerDelegate.applicationLogger, currentLockHolderObject.lockRef+" forcibly released");
		}    	
    }
    
    private static ReturnType isTopOfLockStore(String keyspace, String table,
            String primaryKeyValue, String lockReference)
            throws MusicLockingException, MusicQueryException, MusicServiceException {

        // return failure to lock holders too early or already evicted from the lock store
        String topOfLockStoreS =
                getLockingServiceHandle().peekLockQueue(keyspace, table, primaryKeyValue).lockRef;
        long topOfLockStoreL = Long.parseLong(topOfLockStoreS);
        long lockReferenceL = Long.parseLong(lockReference);

        if (lockReferenceL > topOfLockStoreL) {
            // only need to check if this is a read lock....
            if (getLockingServiceHandle().isTopOfLockQueue(keyspace, table, primaryKeyValue,
                    lockReference)) {
                return new ReturnType(ResultType.SUCCESS, lockReference + " can read the values");
            }
            logger.info(EELFLoggerDelegate.applicationLogger,
                    lockReference + " is not the lock holder yet");
            return new ReturnType(ResultType.FAILURE,
                    lockReference + " is not the lock holder yet");
        }


        if (lockReferenceL < topOfLockStoreL) {
            logger.info(EELFLoggerDelegate.applicationLogger,
                    lockReference + " is no longer/or was never in the lock store queue");
            return new ReturnType(ResultType.FAILURE,
                    lockReference + " is no longer/or was never in the lock store queue");
        }

        return new ReturnType(ResultType.SUCCESS, lockReference + " is top of lock store");
    }
    
    public  ReturnType acquireLock(String fullyQualifiedKey, String lockReference) throws MusicLockingException, MusicQueryException, MusicServiceException {
        String[] splitString = fullyQualifiedKey.split("\\.");
        String keyspace = splitString[0];
        String table = splitString[1];
        String primaryKeyValue = splitString[2];

        ReturnType result = isTopOfLockStore(keyspace, table, primaryKeyValue, lockReference);
        
        if(result.getResult().equals(ResultType.FAILURE))
        		return result;//not top of the lock store q
    		
        //check to see if the value of the key has to be synced in case there was a forceful release
        String syncTable = keyspace+".unsyncedKeys_"+table;
		String query = "select * from "+syncTable+" where key='"+fullyQualifiedKey+"';";
        PreparedQueryObject readQueryObject = new PreparedQueryObject();
        readQueryObject.appendQueryString(query);
		ResultSet results = MusicDataStoreHandle.getDSHandle().executeQuorumConsistencyGet(readQueryObject);			
		if (results.all().size() != 0) {
			logger.info("In acquire lock: Since there was a forcible release, need to sync quorum!");
			try {
				syncQuorum(keyspace, table, primaryKeyValue);
			} catch (Exception e) {
				StringWriter sw = new StringWriter();
	           	logger.error(EELFLoggerDelegate.errorLogger,e.getMessage(), "[ERR506E] Failed to aquire lock ",ErrorSeverity.CRITICAL, ErrorTypes.LOCKINGERROR);
	            String exceptionAsString = sw.toString();
	            return new ReturnType(ResultType.FAILURE, "Exception thrown while syncing key:\n" + exceptionAsString);			
	        }
			String cleanQuery = "delete  from music_internal.unsynced_keys where key='"+fullyQualifiedKey+"';";
	        PreparedQueryObject deleteQueryObject = new PreparedQueryObject();
	        deleteQueryObject.appendQueryString(cleanQuery);
	        MusicDataStoreHandle.getDSHandle().executePut(deleteQueryObject, "critical");
		}
		
		getLockingServiceHandle().updateLockAcquireTime(keyspace, table, primaryKeyValue, lockReference);
		
		return new ReturnType(ResultType.SUCCESS, lockReference+" is the lock holder for the key");
    }



    /**
     * 
     * @param tableQueryObject
     * @param consistency
     * @return Boolean Indicates success or failure
     * @throws MusicServiceException 
     * 
     * 
     */
    public  ResultType createTable(String keyspace, String table, PreparedQueryObject tableQueryObject, String consistency) throws MusicServiceException {
	    	boolean result = false;
	
	    	try {
		    	//create shadow locking table 
	    		result = getLockingServiceHandle().createLockQueue(keyspace, table);
	    		if(result == false) 
	    			return ResultType.FAILURE;
	
	    		result = false;
	    		
	    		//create table to track unsynced_keys
	    		table = "unsyncedKeys_"+table; 
	    		
	    		String tabQuery = "CREATE TABLE IF NOT EXISTS "+keyspace+"."+table
	    				+ " ( key text,PRIMARY KEY (key) );";
	    		System.out.println(tabQuery);
	    		PreparedQueryObject queryObject = new PreparedQueryObject(); 
	    		
	    		queryObject.appendQueryString(tabQuery);
	    		result = false;
	    		result = MusicDataStoreHandle.getDSHandle().executePut(queryObject, "eventual");

	    	
	    		//create actual table
	    		result = MusicDataStoreHandle.getDSHandle().executePut(tableQueryObject, consistency);
	    	} catch (MusicQueryException | MusicServiceException | MusicLockingException ex) {
	    		logger.error(EELFLoggerDelegate.errorLogger,ex.getMessage(), AppMessages.UNKNOWNERROR  ,ErrorSeverity.WARN, ErrorTypes.MUSICSERVICEERROR);
	    		throw new MusicServiceException(ex.getMessage());
	    	}
	    	return result?ResultType.SUCCESS:ResultType.FAILURE;
    }

    private static void syncQuorum(String keyspace, String table, String primaryKeyValue) throws Exception {
        logger.info(EELFLoggerDelegate.applicationLogger,"Performing sync operation---");
        PreparedQueryObject selectQuery = new PreparedQueryObject();
        PreparedQueryObject updateQuery = new PreparedQueryObject();

        // get the primary key d
        TableMetadata tableInfo = MusicDataStoreHandle.returnColumnMetadata(keyspace, table);
        String primaryKeyName = tableInfo.getPrimaryKey().get(0).getName();// we only support single
                                                                           // primary key
        DataType primaryKeyType = tableInfo.getPrimaryKey().get(0).getType();
        Object cqlFormattedPrimaryKeyValue =
                        MusicUtil.convertToActualDataType(primaryKeyType, primaryKeyValue);

        // get the row of data from a quorum
        selectQuery.appendQueryString("SELECT *  FROM " + keyspace + "." + table + " WHERE "
                        + primaryKeyName + "= ?" + ";");
        selectQuery.addValue(cqlFormattedPrimaryKeyValue);
        ResultSet results = null;
        try {
            results = MusicDataStoreHandle.getDSHandle().executeQuorumConsistencyGet(selectQuery);
            // write it back to a quorum
            Row row = results.one();
            ColumnDefinitions colInfo = row.getColumnDefinitions();
            int totalColumns = colInfo.size();
            int counter = 1;
            StringBuilder fieldValueString = new StringBuilder("");
            for (Definition definition : colInfo) {
                String colName = definition.getName();
                if (colName.equals(primaryKeyName))
                    continue;
                DataType colType = definition.getType();
                Object valueObj = MusicDataStoreHandle.getDSHandle().getColValue(row, colName, colType);
                Object valueString = MusicUtil.convertToActualDataType(colType, valueObj);
                fieldValueString.append(colName + " = ?");
                updateQuery.addValue(valueString);
                if (counter != (totalColumns - 1))
                    fieldValueString.append(",");
                counter = counter + 1;
            }
            updateQuery.appendQueryString("UPDATE " + keyspace + "." + table + " SET "
                            + fieldValueString + " WHERE " + primaryKeyName + "= ? " + ";");
            updateQuery.addValue(cqlFormattedPrimaryKeyValue);

            MusicDataStoreHandle.getDSHandle().executePut(updateQuery, "critical");
        } catch (MusicServiceException | MusicQueryException e) {
        	logger.error(EELFLoggerDelegate.errorLogger,e.getMessage(), AppMessages.QUERYERROR +""+updateQuery ,ErrorSeverity.MAJOR, ErrorTypes.QUERYERROR);
        }
    }




    /**
     * 
     * @param query
     * @return ResultSet
     */
    public  ResultSet quorumGet(PreparedQueryObject query) {
        ResultSet results = null;
        try {
            results = MusicDataStoreHandle.getDSHandle().executeQuorumConsistencyGet(query);
        } catch (MusicServiceException | MusicQueryException e) {
        	logger.error(EELFLoggerDelegate.errorLogger,e.getMessage(), AppMessages.UNKNOWNERROR ,ErrorSeverity.MAJOR, ErrorTypes.GENERALSERVICEERROR);
        
        }
        return results;

    }



    /**
     * 
     * @param fullyQualifiedKey lockName
     * @return
     */
    public  String whoseTurnIsIt(String fullyQualifiedKey) {
        String[] splitString = fullyQualifiedKey.split("\\.");
        String keyspace = splitString[0];
        String table = splitString[1];
        String primaryKeyValue = splitString[2];
        try {
            return getLockingServiceHandle().peekLockQueue(keyspace, table, primaryKeyValue).lockRef;
        } catch (MusicLockingException | MusicServiceException | MusicQueryException e) {
         	logger.error(EELFLoggerDelegate.errorLogger,e.getMessage(), AppMessages.LOCKINGERROR+fullyQualifiedKey ,ErrorSeverity.CRITICAL, ErrorTypes.LOCKINGERROR);
        }
        return null;
    }

    /**
     * 
     * @param lockReference
     * @return
     */
    public static String getLockNameFromId(String lockReference) {
        StringTokenizer st = new StringTokenizer(lockReference);
        return st.nextToken("$");
    }

    public  MusicLockState destroyLockRef(String fullyQualifiedKey, String lockReference) {
        long start = System.currentTimeMillis();
        String[] splitString = fullyQualifiedKey.split("\\.");
        String keyspace = splitString[0];
        String table = splitString[1];
        String primaryKeyValue = splitString[2];
        try {
            getLockingServiceHandle().deQueueLockRef(keyspace, table, primaryKeyValue, lockReference);
        } catch (MusicLockingException | MusicServiceException | MusicQueryException e) {
        	logger.error(EELFLoggerDelegate.errorLogger,e.getMessage(), AppMessages.DESTROYLOCK+lockReference  ,ErrorSeverity.CRITICAL, ErrorTypes.LOCKINGERROR);
        } 
        long end = System.currentTimeMillis();
        logger.info(EELFLoggerDelegate.applicationLogger,"Time taken to destroy lock reference:" + (end - start) + " ms");
        return getMusicLockState(fullyQualifiedKey);
    }

    public   MusicLockState  voluntaryReleaseLock(String fullyQualifiedKey, String lockReference) throws MusicLockingException{
		return destroyLockRef(fullyQualifiedKey, lockReference);
	}

    public  MusicLockState  forciblyReleaseLock(String fullyQualifiedKey, String lockReference) throws MusicLockingException, MusicServiceException, MusicQueryException{
        String[] splitString = fullyQualifiedKey.split("\\.");
        String keyspace = splitString[0];
        String table = splitString[1];

    		//leave a signal that this key could potentially be unsynchronized
        String syncTable = keyspace+".unsyncedKeys_"+table;	
        PreparedQueryObject queryObject = new PreparedQueryObject();
		String values = "(?)";
		queryObject.addValue(fullyQualifiedKey);
		String insQuery = "insert into "+syncTable+" (key) values "+values+";";
        queryObject.appendQueryString(insQuery);
        MusicDataStoreHandle.getDSHandle().executePut(queryObject, "critical");	
        
        //now release the lock
		return destroyLockRef(fullyQualifiedKey, lockReference);
	}

    /**
     * 
     * @param lockName
     * @throws MusicLockingException 
     */
    public  void deleteLock(String lockName) throws MusicLockingException {
    		//deprecated
    	}

    // Prepared Query Additions.

    /**
     * 
     * @param queryObject
     * @return ReturnType
     * @throws MusicServiceException
     */
    public  ReturnType eventualPut(PreparedQueryObject queryObject) {
        boolean result = false;
        try {
            result = MusicDataStoreHandle.getDSHandle().executePut(queryObject, MusicUtil.EVENTUAL);
        } catch (MusicServiceException | MusicQueryException ex) {
        	logger.error(EELFLoggerDelegate.errorLogger,ex.getMessage(), "[ERR512E] Failed to get ZK Lock Handle "  ,ErrorSeverity.WARN, ErrorTypes.MUSICSERVICEERROR);
            logger.error(EELFLoggerDelegate.errorLogger,ex.getMessage() + "  " + ex.getCause() + " " + ex);
            return new ReturnType(ResultType.FAILURE, ex.getMessage());
        }
        if (result) {
            return new ReturnType(ResultType.SUCCESS, "Success");
        } else {
            return new ReturnType(ResultType.FAILURE, "Failure");
        }
    }
    
    /**
     * 
     * @param keyspace
     * @param table
     * @param primaryKeyValue
     * @param queryObject
     * @param lockReference
     * @return
     */
    public  ReturnType criticalPut(String keyspace, String table, String primaryKeyValue,
                    PreparedQueryObject queryObject, String lockReference, Condition conditionInfo) {
        long start = System.currentTimeMillis();
        try {
        ReturnType result = isTopOfLockStore(keyspace, table, primaryKeyValue, lockReference);
        if(result.getResult().equals(ResultType.FAILURE))
        		return result;//not top of the lock store q

        if (conditionInfo != null)
            try {
              if (conditionInfo.testCondition() == false)
                  return new ReturnType(ResultType.FAILURE,
                                  "Lock acquired but the condition is not true");
            } catch (Exception e) {
              return new ReturnType(ResultType.FAILURE,
                      "Exception thrown while checking the condition, check its sanctity:\n"
                                      + e.getMessage());
            }
        	 
          String query = queryObject.getQuery();
          long timeOfWrite = System.currentTimeMillis();
          long lockOrdinal = Long.parseLong(lockReference);
          long ts = MusicUtil.v2sTimeStampInMicroseconds(lockOrdinal, timeOfWrite);
          // TODO: use Statement instead of modifying query
          query = query.replaceFirst("SET", "USING TIMESTAMP "+ ts + " SET");
    	  queryObject.replaceQueryString(query);
          MusicDataStore dsHandle = MusicDataStoreHandle.getDSHandle();
          dsHandle.executePut(queryObject, MusicUtil.CRITICAL);
          long end = System.currentTimeMillis();
          logger.info(EELFLoggerDelegate.applicationLogger,"Time taken for the critical put:" + (end - start) + " ms");
        }catch (MusicQueryException | MusicServiceException | MusicLockingException  e) {
            logger.error(EELFLoggerDelegate.errorLogger,e.getMessage());
            return new ReturnType(ResultType.FAILURE,
                            "Exception thrown while doing the critical put\n"
                                            + e.getMessage());
        }
         return new ReturnType(ResultType.SUCCESS, "Update performed");
    }

    
    /**
     * 
     * @param queryObject
     * @param consistency
     * @return Boolean Indicates success or failure
     * @throws MusicServiceException 
     * 
     * 
     */
    public  ResultType nonKeyRelatedPut(PreparedQueryObject queryObject, String consistency) throws MusicServiceException {
        // this is mainly for some functions like keyspace creation etc which does not
        // really need the bells and whistles of Music locking.
        boolean result = false;
        try {
            result = MusicDataStoreHandle.getDSHandle().executePut(queryObject, consistency);
        } catch (MusicQueryException | MusicServiceException ex) {
        	logger.error(EELFLoggerDelegate.errorLogger, ex.getMessage(), AppMessages.UNKNOWNERROR,
                    ErrorSeverity.WARN, ErrorTypes.MUSICSERVICEERROR);
            throw new MusicServiceException(ex.getMessage());
        }
        return result ? ResultType.SUCCESS : ResultType.FAILURE;
    }

    /**
     * This method performs DDL operation on cassandra.
     * 
     * @param queryObject query object containing prepared query and values
     * @return ResultSet
     * @throws MusicServiceException 
     */
    public  ResultSet get(PreparedQueryObject queryObject) throws MusicServiceException {
        ResultSet results = null;
        try {
			results = MusicDataStoreHandle.getDSHandle().executeOneConsistencyGet(queryObject);
        } catch (MusicQueryException | MusicServiceException e) {
            logger.error(EELFLoggerDelegate.errorLogger,e.getMessage());
            throw new MusicServiceException(e.getMessage());
        }
        return results;
    }

    /**
     * This method performs DDL operations on cassandra, if the the resource is available. Lock ID
     * is used to check if the resource is free.
     * 
     * @param keyspace name of the keyspace
     * @param table name of the table
     * @param primaryKeyValue primary key value
     * @param queryObject query object containing prepared query and values
     * @param lockReference lock ID to check if the resource is free to perform the operation.
     * @return ResultSet
     */
    public  ResultSet criticalGet(String keyspace, String table, String primaryKeyValue,
                    PreparedQueryObject queryObject, String lockReference) throws MusicServiceException {
        ResultSet results = null;
        
        try {
            ReturnType result = isTopOfLockStore(keyspace, table, primaryKeyValue, lockReference);
            if(result.getResult().equals(ResultType.FAILURE))
            		return null;//not top of the lock store q
                results = MusicDataStoreHandle.getDSHandle().executeQuorumConsistencyGet(queryObject);
        } catch (MusicQueryException | MusicServiceException | MusicLockingException e) {
        		logger.error(EELFLoggerDelegate.errorLogger,e.getMessage(), AppMessages.UNKNOWNERROR  ,ErrorSeverity.WARN, ErrorTypes.MUSICSERVICEERROR);
        }
        return results;
    }

    /**
     * This method performs DML operation on cassandra, when the lock of the dd is acquired.
     * 
     * @param keyspaceName name of the keyspace
     * @param tableName name of the table
     * @param primaryKey primary key value
     * @param queryObject query object containing prepared query and values
     * @return ReturnType
     * @throws MusicLockingException 
     * @throws MusicServiceException 
     * @throws MusicQueryException 
     */
    public  ReturnType atomicPut(String keyspaceName, String tableName, String primaryKey,
                    PreparedQueryObject queryObject, Condition conditionInfo) throws MusicLockingException, MusicQueryException, MusicServiceException {
        long start = System.currentTimeMillis();
        String fullyQualifiedKey = keyspaceName + "." + tableName + "." + primaryKey;
        String lockReference = createLockReference(fullyQualifiedKey);
        long lockCreationTime = System.currentTimeMillis();
        ReturnType lockAcqResult = acquireLock(fullyQualifiedKey, lockReference);
        long lockAcqTime = System.currentTimeMillis();

        if (!lockAcqResult.getResult().equals(ResultType.SUCCESS)) {
            logger.info(EELFLoggerDelegate.applicationLogger,"unable to acquire lock, id " + lockReference);
            voluntaryReleaseLock(fullyQualifiedKey,lockReference);
            return lockAcqResult;
        }

        logger.info(EELFLoggerDelegate.applicationLogger,"acquired lock with id " + lockReference);
        ReturnType criticalPutResult = criticalPut(keyspaceName, tableName, primaryKey,
                        queryObject, lockReference, conditionInfo);
        long criticalPutTime = System.currentTimeMillis();
        voluntaryReleaseLock(fullyQualifiedKey,lockReference);
        long lockDeleteTime = System.currentTimeMillis();
        String timingInfo = "|lock creation time:" + (lockCreationTime - start)
                        + "|lock accquire time:" + (lockAcqTime - lockCreationTime)
                        + "|critical put time:" + (criticalPutTime - lockAcqTime)
                        + "|lock delete time:" + (lockDeleteTime - criticalPutTime) + "|";
        criticalPutResult.setTimingInfo(timingInfo);
        return criticalPutResult;
    }
    



    /**
     * This method performs DDL operation on cassasndra, when the lock for the resource is acquired.
     * 
     * @param keyspaceName name of the keyspace
     * @param tableName name of the table
     * @param primaryKey primary key value
     * @param queryObject query object containing prepared query and values
     * @return ResultSet
     * @throws MusicServiceException
     * @throws MusicLockingException 
     * @throws MusicQueryException 
     */
    public  ResultSet atomicGet(String keyspaceName, String tableName, String primaryKey,
                    PreparedQueryObject queryObject) throws MusicServiceException, MusicLockingException, MusicQueryException {
        String fullyQualifiedKey = keyspaceName + "." + tableName + "." + primaryKey;
        String lockReference = createLockReference(fullyQualifiedKey);
        long leasePeriod = MusicUtil.getDefaultLockLeasePeriod();
        ReturnType lockAcqResult = acquireLock(fullyQualifiedKey, lockReference);
        if (lockAcqResult.getResult().equals(ResultType.SUCCESS)) {
            logger.info(EELFLoggerDelegate.applicationLogger,"acquired lock with id " + lockReference);
            ResultSet result =
                            criticalGet(keyspaceName, tableName, primaryKey, queryObject, lockReference);
            voluntaryReleaseLock(fullyQualifiedKey,lockReference);
            return result;
        } else {
            voluntaryReleaseLock(fullyQualifiedKey,lockReference);
            logger.info(EELFLoggerDelegate.applicationLogger,"unable to acquire lock, id " + lockReference);
            return null;
        }
    }
    
    
    public static MusicLockState getMusicLockState(String fullyQualifiedKey) {
    		return null;
    }

  
    
    /**
     * @param lockName
     * @return
     */
    public static Map<String, Object> validateLock(String lockName) {
        Map<String, Object> resultMap = new HashMap<>();
        String[] locks = lockName.split("\\.");
        if(locks.length < 3) {
            resultMap.put("Exception", "Invalid lock. Please make sure lock is of the type keyspaceName.tableName.primaryKey");
            return resultMap;
        }
        String keyspace= locks[0];
        if(keyspace.startsWith("$"))
            keyspace = keyspace.substring(1);
        resultMap.put("keyspace",keyspace);
        return resultMap;
    }
    

	public static void main(String[] args) {
		String x = "axe top";
		x = x.replaceFirst("top", "sword");
		System.out.print(x); //returns sword pickaxe
	}



	@Override
	public ReturnType atomicPutWithDeleteLock(String keyspaceName, String tableName, String primaryKey,
			PreparedQueryObject queryObject, Condition conditionInfo) throws MusicLockingException {
		//Deprecated
		return null;
	}
	@Override
	public List<String> getLockQueue(String fullyQualifiedKey)
			throws MusicServiceException, MusicQueryException, MusicLockingException {
    	String[] splitString = fullyQualifiedKey.split("\\.");
        String keyspace = splitString[0];
        String table = splitString[1];
        String primaryKeyValue = splitString[2];

        return getLockingServiceHandle().getLockQueue(keyspace, table, primaryKeyValue);
	}
	@Override
	public long getLockQueueSize(String fullyQualifiedKey)
			throws MusicServiceException, MusicQueryException, MusicLockingException {
    	String[] splitString = fullyQualifiedKey.split("\\.");
        String keyspace = splitString[0];
        String table = splitString[1];
        String primaryKeyValue = splitString[2];

        return getLockingServiceHandle().getLockQueueSize(keyspace, table, primaryKeyValue);
	}
}