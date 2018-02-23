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
package org.onap.music.main;


import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

import org.onap.music.datastore.MusicDataStore;
import org.onap.music.datastore.PreparedQueryObject;
import org.onap.music.datastore.jsonobjects.JsonKeySpace;
import org.onap.music.eelf.logging.EELFLoggerDelegate;
// import org.onap.music.eelf.logging.EELFLoggerDelegate;
import org.onap.music.exceptions.MusicLockingException;
import org.onap.music.exceptions.MusicQueryException;
import org.onap.music.exceptions.MusicServiceException;
import org.onap.music.lockingservice.MusicLockState;
import org.onap.music.lockingservice.MusicLockState.LockStatus;
import org.onap.music.lockingservice.MusicLockingService;

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
public class MusicCore {

    public static MusicLockingService mLockHandle = null;
    public static MusicDataStore mDstoreHandle = null;
    private static EELFLoggerDelegate logger = EELFLoggerDelegate.getLogger(MusicCore.class);

    public static class Condition {
        Map<String, Object> conditions;
        PreparedQueryObject selectQueryForTheRow;

        public Condition(Map<String, Object> conditions, PreparedQueryObject selectQueryForTheRow) {
            this.conditions = conditions;
            this.selectQueryForTheRow = selectQueryForTheRow;
        }

        public boolean testCondition() {
            // first generate the row
            ResultSet results = quorumGet(selectQueryForTheRow);
            Row row = results.one();
            return getDSHandle().doesRowSatisfyCondition(row, conditions);
        }
    }


    public static MusicLockingService getLockingServiceHandle() throws MusicLockingException {
        logger.info(EELFLoggerDelegate.applicationLogger,"Acquiring lock store handle");
        long start = System.currentTimeMillis();

        if (mLockHandle == null) {
            try {
                mLockHandle = new MusicLockingService();
            } catch (Exception e) {
                logger.error(EELFLoggerDelegate.errorLogger,"Failed to aquire Locl store handle" + e.getMessage());
                throw new MusicLockingException("Failed to aquire Locl store handle " + e);
            }
        }
        long end = System.currentTimeMillis();
        logger.info(EELFLoggerDelegate.applicationLogger,"Time taken to acquire lock store handle:" + (end - start) + " ms");
        return mLockHandle;
    }

    /**
     * 
     * @param remoteIp
     * @return
     */
    public static MusicDataStore getDSHandle(String remoteIp) {
        logger.info(EELFLoggerDelegate.applicationLogger,"Acquiring data store handle");
        long start = System.currentTimeMillis();
        if (mDstoreHandle == null) {
            mDstoreHandle = new MusicDataStore(remoteIp);
        }
        long end = System.currentTimeMillis();
        logger.info(EELFLoggerDelegate.applicationLogger,"Time taken to acquire data store handle:" + (end - start) + " ms");
        return mDstoreHandle;
    }

    /**
     * 
     * @return
     */
    public static MusicDataStore getDSHandle() {
        logger.info(EELFLoggerDelegate.applicationLogger,"Acquiring data store handle");
        long start = System.currentTimeMillis();
        if (mDstoreHandle == null) {
            mDstoreHandle = new MusicDataStore();
        }
        long end = System.currentTimeMillis();
        logger.info(EELFLoggerDelegate.applicationLogger,"Time taken to acquire data store handle:" + (end - start) + " ms");
        return mDstoreHandle;
    }

    public static String createLockReference(String lockName) {
        logger.info(EELFLoggerDelegate.applicationLogger,"Creating lock reference for lock name:" + lockName);
        long start = System.currentTimeMillis();
        String lockId = null;
        try {
            lockId = getLockingServiceHandle().createLockId("/" + lockName);
        } catch (MusicLockingException e) {
            logger.error(EELFLoggerDelegate.errorLogger,"Failed to create Lock Reference " + lockName);
        }
        long end = System.currentTimeMillis();
        logger.info(EELFLoggerDelegate.applicationLogger,"Time taken to create lock reference:" + (end - start) + " ms");
        return lockId;
    }

    /**
     * 
     * @param key
     * @return
     */
    public static boolean isTableOrKeySpaceLock(String key) {
        String[] splitString = key.split("\\.");
        if (splitString.length > 2)
            return false;
        else
            return true;
    }

    /**
     * 
     * @param key
     * @return
     */
    public static MusicLockState getMusicLockState(String key) {
        long start = System.currentTimeMillis();
        try {
            String[] splitString = key.split("\\.");
            String keyspaceName = splitString[0];
            String tableName = splitString[1];
            String primaryKey = splitString[2];
            MusicLockState mls;
            String lockName = keyspaceName + "." + tableName + "." + primaryKey;
            mls = getLockingServiceHandle().getLockState(lockName);
            long end = System.currentTimeMillis();
            logger.info(EELFLoggerDelegate.applicationLogger,"Time taken to get lock state:" + (end - start) + " ms");
            return mls;
        } catch (NullPointerException | MusicLockingException e) {
            logger.error(EELFLoggerDelegate.errorLogger,"No lock object exists as of now.." + e);
        }
        return null;
    }

    public static ReturnType acquireLockWithLease(String key, String lockId, long leasePeriod) {
        try {
            long start = System.currentTimeMillis();
            /* check if the current lock has exceeded its lease and if yes, release that lock */
            MusicLockState mls = getMusicLockState(key);
            if (mls != null) {
                if (mls.getLockStatus().equals(LockStatus.LOCKED)) {
                    logger.info(EELFLoggerDelegate.applicationLogger,"The current lock holder for " + key + " is " + mls.getLockHolder()
                                    + ". Checking if it has exceeded lease");
                    long currentLockPeriod = System.currentTimeMillis() - mls.getLeaseStartTime();
                    long currentLeasePeriod = mls.getLeasePeriod();
                    if (currentLockPeriod > currentLeasePeriod) {
                        logger.info(EELFLoggerDelegate.applicationLogger,"Lock period " + currentLockPeriod
                                        + " has exceeded lease period " + currentLeasePeriod);
                        boolean voluntaryRelease = false;
                        String currentLockHolder = mls.getLockHolder();
                        mls = releaseLock(currentLockHolder, voluntaryRelease);
                    }
                }
            } else
                logger.debug("There is no lock state object for " + key);

            /*
             * call the traditional acquire lock now and if the result returned is true, set the
             * begin time-stamp and lease period
             */
            if (acquireLock(key, lockId).getResult() == ResultType.SUCCESS) {
                mls = getMusicLockState(key);// get latest state
                if (mls.getLeaseStartTime() == -1) {// set it again only if it is not set already
                    mls.setLeaseStartTime(System.currentTimeMillis());
                    mls.setLeasePeriod(leasePeriod);
                    getLockingServiceHandle().setLockState(key, mls);
                }
                long end = System.currentTimeMillis();
                logger.info(EELFLoggerDelegate.applicationLogger,"Time taken to acquire leased lock:" + (end - start) + " ms");
                return new ReturnType(ResultType.SUCCESS, "Accquired lock");
            } else {
                long end = System.currentTimeMillis();
                logger.info(EELFLoggerDelegate.applicationLogger,"Time taken to fail to acquire leased lock:" + (end - start) + " ms");
                return new ReturnType(ResultType.FAILURE, "Could not acquire lock");
            }
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            logger.error(EELFLoggerDelegate.errorLogger,e.getMessage());
            String exceptionAsString = sw.toString();
            return new ReturnType(ResultType.FAILURE,
                            "Exception thrown in acquireLockWithLease:\n" + exceptionAsString);
        }
    }

    public static ReturnType acquireLock(String key, String lockId) {
        /*
         * first check if I am on top. Since ids are not reusable there is no need to check
         * lockStatus If the status is unlocked, then the above call will automatically return
         * false.
         */
        Boolean result = false;
        try {
            result = getLockingServiceHandle().isMyTurn(lockId);
        } catch (MusicLockingException e2) {
            logger.error(EELFLoggerDelegate.errorLogger,"Failed to aquireLock lockId " + lockId + " " + e2);
        }
        if (result == false) {
            logger.info(EELFLoggerDelegate.applicationLogger,"In acquire lock: Not your turn, someone else has the lock");
            try {
				if (!getLockingServiceHandle().lockIdExists(lockId)) {
					logger.info(EELFLoggerDelegate.applicationLogger, "In acquire lock: this lockId doesn't exist");
					return new ReturnType(ResultType.FAILURE, "Lockid doesn't exist");
				}
			} catch (MusicLockingException e) {
				logger.error(EELFLoggerDelegate.errorLogger,"Failed to check if lockid exists - lockId " + lockId + " " + e);
			}
            logger.info(EELFLoggerDelegate.applicationLogger,"In acquire lock: returning failure");
            return new ReturnType(ResultType.FAILURE, "Not your turn, someone else has the lock");
        }


        // this is for backward compatibility where locks could also be acquired on just
        // keyspaces or tables.
        if (isTableOrKeySpaceLock(key) == true) {
            logger.info(EELFLoggerDelegate.applicationLogger,"In acquire lock: A table or keyspace lock so no need to perform sync...so returning true");
            return new ReturnType(ResultType.SUCCESS, "A table or keyspace lock so no need to perform sync...so returning true");
        }

        // read the lock name corresponding to the key and if the status is locked or being locked,
        // then return false
        MusicLockState currentMls = null;
        MusicLockState newMls = null;
        try {
            currentMls = getMusicLockState(key);
            String currentLockHolder = currentMls.getLockHolder();
            if (lockId.equals(currentLockHolder)) {
                logger.info(EELFLoggerDelegate.applicationLogger,"In acquire lock: You already have the lock!");
                return new ReturnType(ResultType.SUCCESS, "You already have the lock!");
            }
        } catch (NullPointerException e) {
            logger.error(EELFLoggerDelegate.errorLogger,"In acquire lock:No one has tried to acquire the lock yet..");
        }

        // change status to "being locked". This state transition is necessary to ensure syncing
        // before granting the lock
        String lockHolder = null;
        boolean needToSyncQuorum = false;
        if (currentMls != null)
            needToSyncQuorum = currentMls.isNeedToSyncQuorum();


        newMls = new MusicLockState(MusicLockState.LockStatus.BEING_LOCKED, lockHolder,
                        needToSyncQuorum);
        try {
            getLockingServiceHandle().setLockState(key, newMls);
        } catch (MusicLockingException e1) {
            logger.error(EELFLoggerDelegate.errorLogger,"Failed to set Lock state " + key + " " + e1);
        }
        logger.info(EELFLoggerDelegate.applicationLogger,"In acquire lock: Set lock state to being_locked");

        // do syncing if this was a forced lock release
        if (needToSyncQuorum) {
            logger.info(EELFLoggerDelegate.applicationLogger,"In acquire lock: Since there was a forcible release, need to sync quorum!");
            syncQuorum(key);
        }

        // change status to locked
        lockHolder = lockId;
        needToSyncQuorum = false;
        newMls = new MusicLockState(MusicLockState.LockStatus.LOCKED, lockHolder, needToSyncQuorum);
        try {
            getLockingServiceHandle().setLockState(key, newMls);
        } catch (MusicLockingException e) {
            logger.error(EELFLoggerDelegate.errorLogger,"Failed to set Lock state " + key + " " + e);
        }
        logger.info(EELFLoggerDelegate.applicationLogger,"In acquire lock: Set lock state to locked and assigned current lock ref "
                        + lockId + " as holder");
        
        return new ReturnType(result?ResultType.SUCCESS:ResultType.FAILURE, "Set lock state to locked and assigned a lock holder");
    }



    /**
     * 
     * @param keyspaceName
     * @param kspObject
     * @return
     * @throws Exception
     */
    public boolean createKeyspace(String keyspaceName, JsonKeySpace kspObject) throws Exception {
        return true;
    }


    private static void syncQuorum(String key) {
        logger.info(EELFLoggerDelegate.applicationLogger,"Performing sync operation---");
        String[] splitString = key.split("\\.");
        String keyspaceName = splitString[0];
        String tableName = splitString[1];
        String primaryKeyValue = splitString[2];
        PreparedQueryObject selectQuery = new PreparedQueryObject();
        PreparedQueryObject updateQuery = new PreparedQueryObject();

        // get the primary key d
        TableMetadata tableInfo = returnColumnMetadata(keyspaceName, tableName);
        String primaryKeyName = tableInfo.getPrimaryKey().get(0).getName();// we only support single
                                                                           // primary key
        DataType primaryKeyType = tableInfo.getPrimaryKey().get(0).getType();
        Object cqlFormattedPrimaryKeyValue =
                        MusicUtil.convertToActualDataType(primaryKeyType, primaryKeyValue);

        // get the row of data from a quorum
        selectQuery.appendQueryString("SELECT *  FROM " + keyspaceName + "." + tableName + " WHERE "
                        + primaryKeyName + "= ?" + ";");
        selectQuery.addValue(cqlFormattedPrimaryKeyValue);
        // String selectQuery = "SELECT * FROM "+keyspaceName+"."+tableName+ " WHERE
        // "+primaryKeyName+"="+cqlFormattedPrimaryKeyValue+";";
        ResultSet results = null;
        try {
            results = getDSHandle().executeCriticalGet(selectQuery);
            // write it back to a quorum
            Row row = results.one();
            ColumnDefinitions colInfo = row.getColumnDefinitions();
            int totalColumns = colInfo.size();
            int counter = 1;
            // String fieldValueString="";
            StringBuilder fieldValueString = new StringBuilder("");
            for (Definition definition : colInfo) {
                String colName = definition.getName();
                if (colName.equals(primaryKeyName))
                    continue;
                DataType colType = definition.getType();
                Object valueObj = getDSHandle().getColValue(row, colName, colType);
                Object valueString = MusicUtil.convertToActualDataType(colType, valueObj);
                // fieldValueString = fieldValueString+ colName+"="+valueString;
                fieldValueString.append(colName + " = ?");
                updateQuery.addValue(valueString);
                if (counter != (totalColumns - 1))
                    fieldValueString.append(",");
                counter = counter + 1;
            }
            updateQuery.appendQueryString("UPDATE " + keyspaceName + "." + tableName + " SET "
                            + fieldValueString + " WHERE " + primaryKeyName + "= ? " + ";");
            updateQuery.addValue(cqlFormattedPrimaryKeyValue);
            // String updateQuery = "UPDATE "+keyspaceName+"."+tableName+" SET "+fieldValueString+"
            // WHERE "+primaryKeyName+"="+cqlFormattedPrimaryKeyValue+";";

            getDSHandle().executePut(updateQuery, "critical");
        } catch (MusicServiceException | MusicQueryException e) {
            logger.error(EELFLoggerDelegate.errorLogger,"Failed to execute update query " + updateQuery + " " + e);
        }
    }




    /**
     * 
     * @param query
     * @return ResultSet
     */
    public static ResultSet quorumGet(PreparedQueryObject query) {
        ResultSet results = null;
        try {
            results = getDSHandle().executeCriticalGet(query);
        } catch (MusicServiceException | MusicQueryException e) {
            logger.error(EELFLoggerDelegate.errorLogger,e.getMessage());
        }
        return results;

    }

    /**
     * 
     * @param results
     * @return
     */
    public static Map<String, HashMap<String, Object>> marshallResults(ResultSet results) {
        return getDSHandle().marshalData(results);
    }

    /**
     * 
     * @param lockName
     * @return
     */
    public static String whoseTurnIsIt(String lockName) {

        try {
            return getLockingServiceHandle().whoseTurnIsIt("/" + lockName) + "";
        } catch (MusicLockingException e) {
            logger.error(EELFLoggerDelegate.errorLogger,"Failed whoseTurnIsIt  " + lockName + " " + e);
        }
        return null;


    }

    /**
     * 
     * @param lockId
     * @return
     */
    public static String getLockNameFromId(String lockId) {
        StringTokenizer st = new StringTokenizer(lockId);
        return st.nextToken("$");
    }

    public static void destroyLockRef(String lockId) {
        long start = System.currentTimeMillis();
        try {
            getLockingServiceHandle().unlockAndDeleteId(lockId);
        } catch (MusicLockingException e) {
            logger.error(EELFLoggerDelegate.errorLogger,"Failed to Destroy Lock Ref  " + lockId + " " + e);
        }
        long end = System.currentTimeMillis();
        logger.info(EELFLoggerDelegate.applicationLogger,"Time taken to destroy lock reference:" + (end - start) + " ms");
    }

    public static MusicLockState releaseLock(String lockId, boolean voluntaryRelease) {
        long start = System.currentTimeMillis();
        try {
            getLockingServiceHandle().unlockAndDeleteId(lockId);
        } catch (MusicLockingException e1) {
            logger.error(EELFLoggerDelegate.errorLogger,"Failed to release Lock " + lockId + " " + e1);
        }
        String lockName = getLockNameFromId(lockId);
        MusicLockState mls;
        String lockHolder = null;
        if (voluntaryRelease) {
            mls = new MusicLockState(MusicLockState.LockStatus.UNLOCKED, lockHolder);
            logger.info(EELFLoggerDelegate.applicationLogger,"In unlock: lock voluntarily released for " + lockId);
        } else {
            boolean needToSyncQuorum = true;
            mls = new MusicLockState(MusicLockState.LockStatus.UNLOCKED, lockHolder,
                            needToSyncQuorum);
            logger.info(EELFLoggerDelegate.applicationLogger,"In unlock: lock forcibly released for " + lockId);
        }
        try {
            getLockingServiceHandle().setLockState(lockName, mls);
        } catch (MusicLockingException e) {
            logger.error(EELFLoggerDelegate.errorLogger,"Failed to release Lock " + lockName + " " + e);
        }
        long end = System.currentTimeMillis();
        logger.info(EELFLoggerDelegate.applicationLogger,"Time taken to release lock:" + (end - start) + " ms");
        return mls;
    }
    
    public static  void  voluntaryReleaseLock(String lockId) throws MusicLockingException{
		getLockingServiceHandle().unlockAndDeleteId(lockId);
	}

    /**
     * 
     * @param lockName
     */
    public static void deleteLock(String lockName) {
        long start = System.currentTimeMillis();
        logger.info(EELFLoggerDelegate.applicationLogger,"Deleting lock for " + lockName);
        try {
            getLockingServiceHandle().deleteLock("/" + lockName);
        } catch (MusicLockingException e) {
            logger.error(EELFLoggerDelegate.errorLogger,"Failed to Delete Lock " + lockName + " " + e);
        }
        long end = System.currentTimeMillis();
        logger.info(EELFLoggerDelegate.applicationLogger,"Time taken to delete lock:" + (end - start) + " ms");
    }



    /**
     * 
     * @param keyspace
     * @param tablename
     * @return
     */
    public static TableMetadata returnColumnMetadata(String keyspace, String tablename) {
        return getDSHandle().returnColumnMetadata(keyspace, tablename);
    }


    /**
     * 
     * @param nodeName
     */
    public static void pureZkCreate(String nodeName) {
        try {
            getLockingServiceHandle().getzkLockHandle().createNode(nodeName);
        } catch (MusicLockingException e) {
            logger.error(EELFLoggerDelegate.errorLogger,"Failed to get ZK Lock Handle " + e);
        }
    }

    /**
     * 
     * @param nodeName
     * @param data
     */
    public static void pureZkWrite(String nodeName, byte[] data) {
        long start = System.currentTimeMillis();
        logger.info(EELFLoggerDelegate.applicationLogger,"Performing zookeeper write to " + nodeName);
        try {
            getLockingServiceHandle().getzkLockHandle().setNodeData(nodeName, data);
        } catch (MusicLockingException e) {
            logger.error(EELFLoggerDelegate.errorLogger,"Failed to get ZK Lock Handle " + e);
        }
        logger.info(EELFLoggerDelegate.applicationLogger,"Performed zookeeper write to " + nodeName);
        long end = System.currentTimeMillis();
        logger.info(EELFLoggerDelegate.applicationLogger,"Time taken for the actual zk put:" + (end - start) + " ms");
    }

    /**
     * 
     * @param nodeName
     * @return
     */
    public static byte[] pureZkRead(String nodeName) {
        long start = System.currentTimeMillis();
        byte[] data = null;
        try {
            data = getLockingServiceHandle().getzkLockHandle().getNodeData(nodeName);
        } catch (MusicLockingException e) {
            logger.error(EELFLoggerDelegate.errorLogger,"Failed to get ZK Lock Handle " + e);
        }
        long end = System.currentTimeMillis();
        logger.info(EELFLoggerDelegate.applicationLogger,"Time taken for the actual zk put:" + (end - start) + " ms");
        return data;
    }



    // Prepared Query Additions.

    /**
     * 
     * @param keyspaceName
     * @param tableName
     * @param primaryKey
     * @param queryObject
     * @return ReturnType
     * @throws MusicServiceException
     */
    public static ReturnType eventualPut(PreparedQueryObject queryObject) {
        boolean result = false;
        try {
            result = getDSHandle().executePut(queryObject, MusicUtil.EVENTUAL);
        } catch (MusicServiceException | MusicQueryException ex) {
            logger.error(EELFLoggerDelegate.errorLogger,ex.getMessage() + "  " + ex.getCause() + " " + ex);
        }
        if (result) {
            return new ReturnType(ResultType.SUCCESS, "Success");
        } else {
            return new ReturnType(ResultType.FAILURE, "Failure");
        }
    }

    /**
     * 
     * @param keyspaceName
     * @param tableName
     * @param primaryKey
     * @param queryObject
     * @param lockId
     * @return
     */
    public static ReturnType criticalPut(String keyspaceName, String tableName, String primaryKey,
                    PreparedQueryObject queryObject, String lockId, Condition conditionInfo) {
        long start = System.currentTimeMillis();

        try {
            MusicLockState mls = getLockingServiceHandle()
                            .getLockState(keyspaceName + "." + tableName + "." + primaryKey);
            if (mls.getLockHolder().equals(lockId) == true) {
                if (conditionInfo != null)// check if condition is true
                    if (conditionInfo.testCondition() == false)
                        return new ReturnType(ResultType.FAILURE,
                                        "Lock acquired but the condition is not true");
                getDSHandle().executePut(queryObject, MusicUtil.CRITICAL);
                long end = System.currentTimeMillis();
                logger.info(EELFLoggerDelegate.applicationLogger,"Time taken for the critical put:" + (end - start) + " ms");
                return new ReturnType(ResultType.SUCCESS, "Update performed");
            } else
                return new ReturnType(ResultType.FAILURE,
                                "Cannot perform operation since you are the not the lock holder");
        } catch (MusicQueryException | MusicServiceException  e) {
            logger.error(EELFLoggerDelegate.errorLogger,e.getMessage());
            return new ReturnType(ResultType.FAILURE,
                            "Exception thrown while doing the critical put, check sanctity of the row/conditions:\n"
                                            + e.getMessage());
        }catch(MusicLockingException ex){
            return new ReturnType(ResultType.FAILURE,ex.getMessage());
        }

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
    public static boolean nonKeyRelatedPut(PreparedQueryObject queryObject, String consistency) throws MusicServiceException {
        // this is mainly for some functions like keyspace creation etc which does not
        // really need the bells and whistles of Music locking.
        boolean result = false;
        try {
            result = getDSHandle().executePut(queryObject, consistency);
        } catch (MusicQueryException | MusicServiceException ex) {
            logger.error(EELFLoggerDelegate.errorLogger,ex.getMessage());
            throw new MusicServiceException(ex.getMessage());
        }
        return result;
    }

    /**
     * This method performs DDL operation on cassandra.
     * 
     * @param queryObject query object containing prepared query and values
     * @return ResultSet
     * @throws MusicServiceException 
     */
    public static ResultSet get(PreparedQueryObject queryObject) throws MusicServiceException {
        ResultSet results = null;
        try {
			results = getDSHandle().executeEventualGet(queryObject);
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
     * @param keyspaceName name of the keyspace
     * @param tableName name of the table
     * @param primaryKey primary key value
     * @param queryObject query object containing prepared query and values
     * @param lockId lock ID to check if the resource is free to perform the operation.
     * @return ResultSet
     */
    public static ResultSet criticalGet(String keyspaceName, String tableName, String primaryKey,
                    PreparedQueryObject queryObject, String lockId) throws MusicServiceException {
        ResultSet results = null;
        try {
            MusicLockState mls = getLockingServiceHandle()
                            .getLockState(keyspaceName + "." + tableName + "." + primaryKey);
            if (mls.getLockHolder().equals(lockId)) {
                results = getDSHandle().executeCriticalGet(queryObject);
            } else
                throw new MusicServiceException("YOU DO NOT HAVE THE LOCK");
        } catch (MusicQueryException | MusicServiceException | MusicLockingException e) {
            logger.error(EELFLoggerDelegate.errorLogger,e.getMessage());
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
     */
    public static ReturnType atomicPut(String keyspaceName, String tableName, String primaryKey,
                    PreparedQueryObject queryObject, Condition conditionInfo) throws MusicLockingException {

        long start = System.currentTimeMillis();
        String key = keyspaceName + "." + tableName + "." + primaryKey;
        String lockId = createLockReference(key);
        long lockCreationTime = System.currentTimeMillis();
        ReturnType lockAcqResult = acquireLock(key, lockId);
        long lockAcqTime = System.currentTimeMillis();
        if (lockAcqResult.getResult().equals(ResultType.SUCCESS)) {
            logger.info(EELFLoggerDelegate.applicationLogger,"acquired lock with id " + lockId);
            ReturnType criticalPutResult = criticalPut(keyspaceName, tableName, primaryKey,
                            queryObject, lockId, conditionInfo);
            long criticalPutTime = System.currentTimeMillis();
            voluntaryReleaseLock(lockId);
            long lockDeleteTime = System.currentTimeMillis();
            String timingInfo = "|lock creation time:" + (lockCreationTime - start)
                            + "|lock accquire time:" + (lockAcqTime - lockCreationTime)
                            + "|critical put time:" + (criticalPutTime - lockAcqTime)
                            + "|lock delete time:" + (lockDeleteTime - criticalPutTime) + "|";
            criticalPutResult.setTimingInfo(timingInfo);
            return criticalPutResult;
        } else {
            logger.info(EELFLoggerDelegate.applicationLogger,"unable to acquire lock, id " + lockId);
            destroyLockRef(lockId);
            return lockAcqResult;
        }
    }
    
    /**
     * this function is mainly for the benchmarks to see the effect of lock deletion.
     * 
     * @param keyspaceName
     * @param tableName
     * @param primaryKey
     * @param queryObject
     * @param conditionInfo
     * @return
     * @throws MusicLockingException 
     */
    public static ReturnType atomicPutWithDeleteLock(String keyspaceName, String tableName,
                    String primaryKey, PreparedQueryObject queryObject, Condition conditionInfo) throws MusicLockingException {

        long start = System.currentTimeMillis();
        String key = keyspaceName + "." + tableName + "." + primaryKey;
        String lockId = createLockReference(key);
        long lockCreationTime = System.currentTimeMillis();
        long leasePeriod = MusicUtil.getDefaultLockLeasePeriod();
        ReturnType lockAcqResult = acquireLock(key, lockId);
        long lockAcqTime = System.currentTimeMillis();
        if (lockAcqResult.getResult().equals(ResultType.SUCCESS)) {
            logger.info(EELFLoggerDelegate.applicationLogger,"acquired lock with id " + lockId);
            ReturnType criticalPutResult = criticalPut(keyspaceName, tableName, primaryKey,
                            queryObject, lockId, conditionInfo);
            long criticalPutTime = System.currentTimeMillis();
            deleteLock(key);
            long lockDeleteTime = System.currentTimeMillis();
            String timingInfo = "|lock creation time:" + (lockCreationTime - start)
                            + "|lock accquire time:" + (lockAcqTime - lockCreationTime)
                            + "|critical put time:" + (criticalPutTime - lockAcqTime)
                            + "|lock delete time:" + (lockDeleteTime - criticalPutTime) + "|";
            criticalPutResult.setTimingInfo(timingInfo);
            return criticalPutResult;
        } else {
            logger.info(EELFLoggerDelegate.applicationLogger,"unable to acquire lock, id " + lockId);
            deleteLock(key);
            return lockAcqResult;
        }
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
     */
    public static ResultSet atomicGet(String keyspaceName, String tableName, String primaryKey,
                    PreparedQueryObject queryObject) throws MusicServiceException, MusicLockingException {
        String key = keyspaceName + "." + tableName + "." + primaryKey;
        String lockId = createLockReference(key);
        long leasePeriod = MusicUtil.getDefaultLockLeasePeriod();
        ReturnType lockAcqResult = acquireLock(key, lockId);
        if (lockAcqResult.getResult().equals(ResultType.SUCCESS)) {
            logger.info(EELFLoggerDelegate.applicationLogger,"acquired lock with id " + lockId);
            ResultSet result =
                            criticalGet(keyspaceName, tableName, primaryKey, queryObject, lockId);
            voluntaryReleaseLock(lockId);
            return result;
        } else {
        	destroyLockRef(lockId);
            logger.info(EELFLoggerDelegate.applicationLogger,"unable to acquire lock, id " + lockId);
            return null;
        }
    }
    
	public static ResultSet atomicGetWithDeleteLock(String keyspaceName, String tableName, String primaryKey,
			PreparedQueryObject queryObject) throws MusicServiceException, MusicLockingException {
		String key = keyspaceName + "." + tableName + "." + primaryKey;
		String lockId = createLockReference(key);
		long leasePeriod = MusicUtil.getDefaultLockLeasePeriod();

		ReturnType lockAcqResult = acquireLock(key, lockId);

		if (lockAcqResult.getResult().equals(ResultType.SUCCESS)) {
			logger.info(EELFLoggerDelegate.applicationLogger, "acquired lock with id " + lockId);
			ResultSet result = criticalGet(keyspaceName, tableName, primaryKey, queryObject, lockId);
			deleteLock(key);
			return result;
		} else {
			deleteLock(key);
			logger.info(EELFLoggerDelegate.applicationLogger, "unable to acquire lock, id " + lockId);
			return null;
		}
	}
    
    

    /**
     * authenticate user logic
     * 
     * @param nameSpace
     * @param userId
     * @param password
     * @param keyspace
     * @param aid
     * @param operation
     * @return
     * @throws Exception
     */
    public static Map<String, Object> autheticateUser(String nameSpace, String userId,
                    String password, String keyspace, String aid, String operation)
                    throws Exception {
        Map<String, Object> resultMap = new HashMap<>();
        String uuid = null;
        resultMap = CachingUtil.validateRequest(nameSpace, userId, password, keyspace, aid,
                        operation);
        if (!resultMap.isEmpty())
            return resultMap;
        boolean isAAF = CachingUtil.isAAFApplication(nameSpace);
        if (!isAAF && !(operation.equals("createKeySpace"))) {
        	if(aid == null) {
        		resultMap.put("Exception", "Aid is mandatory for nonAAF applications ");
        		return resultMap;
        	}
        	if(operation.contains("Lock")) {
        		resultMap = CachingUtil.authenticateAIDUserLock(aid, nameSpace);
        	}
        	else {
        		resultMap = CachingUtil.authenticateAIDUser(aid, keyspace);
        	}
            
            if (!resultMap.isEmpty())
                return resultMap;
        }
        if (aid == null && (userId == null || password == null)) {
            logger.error(EELFLoggerDelegate.errorLogger,"One or more required headers is missing. userId: " + userId
                            + " :: password: " + password);
            resultMap.put("Exception",
                            "UserId and Password are mandatory for the operation " + operation);
            return resultMap;
        }
        
        if (isAAF && nameSpace != null && userId != null && password != null) {
            boolean isValid = true;
            try {
            	 isValid = CachingUtil.authenticateAAFUser(nameSpace, userId, password, keyspace);
            } catch (Exception e) {
                logger.error(EELFLoggerDelegate.errorLogger,"Got exception while AAF authentication for namespace " + nameSpace);
                resultMap.put("Exception", e.getMessage());
            }
            if (!isValid) {
                logger.error(EELFLoggerDelegate.errorLogger,"User not authenticated with AAF.");
                resultMap.put("Exception", "User not authenticated...");
            }
            if (!resultMap.isEmpty())
                return resultMap;

        }

        if (operation.equals("createKeySpace")) {
            logger.info(EELFLoggerDelegate.applicationLogger,"AID is not provided. Creating new UUID for keyspace.");
            PreparedQueryObject pQuery = new PreparedQueryObject();
            pQuery.appendQueryString(
                            "select uuid from admin.keyspace_master where application_name=? and username=? and keyspace_name=? allow filtering");
            pQuery.addValue(MusicUtil.convertToActualDataType(DataType.text(), nameSpace));
            pQuery.addValue(MusicUtil.convertToActualDataType(DataType.text(), userId));
            pQuery.addValue(MusicUtil.convertToActualDataType(DataType.text(),
                            MusicUtil.DEFAULTKEYSPACENAME));

            try {
                Row rs = MusicCore.get(pQuery).one();
                uuid = rs.getUUID("uuid").toString();
                resultMap.put("uuid", "existing");
            } catch (Exception e) {
                logger.info(EELFLoggerDelegate.applicationLogger,"No UUID found in DB. So creating new UUID.");
                uuid = CachingUtil.generateUUID();
                resultMap.put("uuid", "new");
            }

            pQuery = new PreparedQueryObject();
            pQuery.appendQueryString(
                            "INSERT into admin.keyspace_master (uuid, keyspace_name, application_name, is_api, "
                                            + "password, username, is_aaf) values (?,?,?,?,?,?,?)");
            pQuery.addValue(MusicUtil.convertToActualDataType(DataType.uuid(), uuid));
            pQuery.addValue(MusicUtil.convertToActualDataType(DataType.text(), keyspace));
            pQuery.addValue(MusicUtil.convertToActualDataType(DataType.text(), nameSpace));
            pQuery.addValue(MusicUtil.convertToActualDataType(DataType.cboolean(), "True"));
            pQuery.addValue(MusicUtil.convertToActualDataType(DataType.text(), password));
            pQuery.addValue(MusicUtil.convertToActualDataType(DataType.text(), userId));
            pQuery.addValue(MusicUtil.convertToActualDataType(DataType.cboolean(), isAAF));
            CachingUtil.updateMusicCache(uuid, keyspace);
            MusicCore.eventualPut(pQuery);
            resultMap.put("aid", uuid);
        }

        return resultMap;
    }
}
