/*
 * Copyright 2014 mango.jfaster.org
 *
 * The Mango Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.jfaster.mango.transaction;

import org.jfaster.mango.exception.TransactionException;
import org.jfaster.mango.jdbc.DataSourceUtils;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * @author ash
 */
public class TransactionImpl implements Transaction {

    private TransactionContext transactionContext;

    private TransactionState state = TransactionState.RUNNING;

    public TransactionImpl(TransactionContext transactionContext) {
        this.transactionContext = transactionContext;
    }

    @Override
    public void commit() {
        if (state != TransactionState.RUNNING) {
            throw new TransactionException("transaction has commit or rollback");
        }

        Connection conn = transactionContext.getConnection();

        if (conn == null) { // 开启了事务，但是没有dao操作，所以不用回收connection
            state = TransactionState.COMMIT_SUCCESS;
            TransactionSynchronizationManager.clear();
            return;
        }

        try {
            conn.commit();
            state = TransactionState.COMMIT_SUCCESS;
            TransactionSynchronizationManager.clear();
            DataSourceUtils.releaseConnection(conn, transactionContext.getDataSource());
        } catch (SQLException e) {
            // commit出现异常，交由rollback回收connection
            state = TransactionState.COMMIT_FAIL;
            new RuntimeException(); // TODO
        }
    }

    @Override
    public void rollback() {
        if (state != TransactionState.RUNNING && state != TransactionState.COMMIT_FAIL) {
            throw new TransactionException("transaction has rollback or commit");
        }

        Connection conn = transactionContext.getConnection();

        if (conn == null) { // 开启了事务，但是没有dao操作，所以不用回收connection
            state = TransactionState.ROLLBACK_SUCCESS;
            TransactionSynchronizationManager.clear();
            return;
        }

        try {
            conn.rollback();
            state = TransactionState.ROLLBACK_SUCCESS;
        } catch (SQLException e) {
            state = TransactionState.ROLLBACK_FAIL;
            new RuntimeException(); // TODO
        } finally {
            TransactionSynchronizationManager.clear();
            DataSourceUtils.releaseConnection(conn, transactionContext.getDataSource());
        }
    }

    public TransactionState getState() {
        return state;
    }
}
