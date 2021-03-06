/*
 * ============LICENSE_START========================================== org.onap.music
 * =================================================================== Copyright (c) 2017 AT&T
 * Intellectual Property ===================================================================
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 * 
 * ============LICENSE_END=============================================
 * ====================================================================
 */

package org.onap.music.lockingservice;


import java.io.IOException;
import java.util.StringTokenizer;
import java.util.concurrent.CountDownLatch;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.ZooKeeper;
import org.onap.music.eelf.logging.EELFLoggerDelegate;
import org.onap.music.eelf.logging.format.AppMessages;
import org.onap.music.eelf.logging.format.ErrorSeverity;
import org.onap.music.eelf.logging.format.ErrorTypes;
import org.onap.music.exceptions.MusicLockingException;
import org.onap.music.exceptions.MusicServiceException;
import org.onap.music.main.MusicUtil;
import java.util.concurrent.TimeUnit;


public class MusicLockingService implements Watcher {

    
    private static final int SESSION_TIMEOUT = 180000;
    ZkStatelessLockService zkLockHandle = null;
    private CountDownLatch connectedSignal = new CountDownLatch(1);
    private static EELFLoggerDelegate logger =
                    EELFLoggerDelegate.getLogger(MusicLockingService.class);

    public MusicLockingService() throws MusicServiceException {
        try {
            ZooKeeper zk = new ZooKeeper(MusicUtil.getMyZkHost(), SESSION_TIMEOUT, this);
            connectedSignal.await();
            zkLockHandle = new ZkStatelessLockService(zk);
        } catch (IOException e) {
            logger.error(EELFLoggerDelegate.errorLogger, e.getMessage(),AppMessages.IOERROR, ErrorSeverity.ERROR, ErrorTypes.CONNECTIONERROR);
            throw new MusicServiceException("IO Error has occured" + e.getMessage());
        } catch (InterruptedException e) {
            logger.error(EELFLoggerDelegate.errorLogger, e.getMessage(),AppMessages.EXECUTIONINTERRUPTED, ErrorSeverity.ERROR, ErrorTypes.LOCKINGERROR);
            throw new MusicServiceException("Exception Occured " + e.getMessage());
        }
    }

    public MusicLockingService(int timeout) throws MusicServiceException { 
        CountDownLatch connectedSignal1 = new CountDownLatch(1); 
        try { 
            ZooKeeper zk1 = new ZooKeeper(MusicUtil.getMyZkHost(), SESSION_TIMEOUT, this); 
            connectedSignal1.await(timeout, TimeUnit.SECONDS); 
            if(!zk1.getState().isConnected()) { 
                throw new MusicServiceException("Unable to Connect. Some nodes are down."); 
            } 
        } catch (IOException e ) { 
            logger.error(EELFLoggerDelegate.errorLogger, e.getMessage(),AppMessages.IOERROR, ErrorSeverity.ERROR, ErrorTypes.CONNECTIONERROR); 
            throw new MusicServiceException("IO Error has occured" + e.getMessage()); 
        } catch (InterruptedException e) { 
            logger.error(EELFLoggerDelegate.errorLogger, e.getMessage(),AppMessages.EXECUTIONINTERRUPTED, ErrorSeverity.ERROR, ErrorTypes.LOCKINGERROR); 
            throw new MusicServiceException("Exception Occured " + e.getMessage()); 
        } 
    }
    
    public ZkStatelessLockService getzkLockHandle() {
        return zkLockHandle;
    }

    public MusicLockingService(String lockServer) {
        try {
            ZooKeeper zk = new ZooKeeper(lockServer, SESSION_TIMEOUT, this);
            connectedSignal.await();
            zkLockHandle = new ZkStatelessLockService(zk);
        } catch (IOException e) {
            logger.error(EELFLoggerDelegate.errorLogger, e.getMessage(),AppMessages.IOERROR, ErrorSeverity.ERROR, ErrorTypes.CONNECTIONERROR);
        }catch( InterruptedException e) {
            logger.error(EELFLoggerDelegate.errorLogger, e.getMessage(),AppMessages.EXECUTIONINTERRUPTED, ErrorSeverity.ERROR, ErrorTypes.LOCKINGERROR);
        }catch(Exception e) {
            logger.error(EELFLoggerDelegate.errorLogger, e.getMessage(),AppMessages.UNKNOWNERROR, ErrorSeverity.ERROR, ErrorTypes.LOCKINGERROR);
        }
    }

    public void createLockaIfItDoesNotExist(String lockName) {
        if (!zkLockHandle.checkIfLockExists(lockName)) {
            String lockHolder = null;
            MusicLockState ml = new MusicLockState(MusicLockState.LockStatus.UNLOCKED, lockHolder);
            byte[] data = ml.serialize();
            zkLockHandle.createLock(lockName, data);
        }
    }

    public void setLockState(String lockName, MusicLockState mls) {
        byte[] data = mls.serialize();
        zkLockHandle.setNodeData(lockName, data);
    }

    public MusicLockState getLockState(String lockName) throws MusicLockingException {

        byte[] data = null;
        try{
            data = zkLockHandle.getNodeData(lockName);
        }catch (Exception ex){
            logger.error(EELFLoggerDelegate.errorLogger, ex.getMessage(),AppMessages.UNKNOWNERROR, ErrorSeverity.ERROR, ErrorTypes.LOCKINGERROR);
        }
        if(data !=null)
        return MusicLockState.deSerialize(data);
        else {
            logger.error(EELFLoggerDelegate.errorLogger,"",AppMessages.INVALIDLOCK, ErrorSeverity.ERROR, ErrorTypes.LOCKINGERROR);
            throw new  MusicLockingException("Invalid lock or acquire failed");
        }
    }

    public String createLockId(String lockName) {
        String lockIdWithSlash = zkLockHandle.createLockId(lockName);
        return lockIdWithSlash.replace('/', '$');
    }

    public boolean isMyTurn(String lockIdWithDollar) {
        String lockId = lockIdWithDollar.replace('$', '/');
        StringTokenizer st = new StringTokenizer(lockId);
        String lockName = "/" + st.nextToken("/");
        try {
            return zkLockHandle.lock(lockName, lockId);
        } catch (KeeperException e) {
            logger.error(EELFLoggerDelegate.errorLogger, e.getMessage(),AppMessages.LOCKINGERROR, ErrorSeverity.ERROR, ErrorTypes.LOCKINGERROR);
        }catch( InterruptedException e) {
            logger.error(EELFLoggerDelegate.errorLogger, e.getMessage(),AppMessages.EXECUTIONINTERRUPTED, ErrorSeverity.ERROR, ErrorTypes.LOCKINGERROR);
        }catch(Exception e) {
            logger.error(EELFLoggerDelegate.errorLogger, e.getMessage(),AppMessages.UNKNOWNERROR, ErrorSeverity.ERROR, ErrorTypes.LOCKINGERROR);
        }
        return false;
    }

    public void unlockAndDeleteId(String lockIdWithDollar) throws KeeperException.NoNodeException {
        String lockId = lockIdWithDollar.replace('$', '/');
        zkLockHandle.unlock(lockId);
    }

    public void deleteLock(String lockName) throws MusicLockingException {
        if(lockIdExists(lockName))
            zkLockHandle.deleteLock(lockName);
        else{
            throw new MusicLockingException("Lock does not exist.Please check the lock: " + lockName + " and try again");
        }
    }

    public String whoseTurnIsIt(String lockName) {
        String lockHolder = zkLockHandle.currentLockHolder(lockName);
        return lockHolder.replace('/', '$');

    }

    public void process(WatchedEvent event) { // Watcher interface
        if (event.getState() == KeeperState.SyncConnected) {
            connectedSignal.countDown();
        }
    }


    public void close() {
        zkLockHandle.close();
    }

    public boolean lockIdExists(String lockIdWithDollar) {
        String lockId = lockIdWithDollar.replace('$', '/');
        return zkLockHandle.checkIfLockExists(lockId);
    }

}
