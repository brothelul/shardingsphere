/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.shardingproxy.backend.communication.jdbc.wrapper;

import lombok.RequiredArgsConstructor;
import org.apache.shardingsphere.core.PreparedQueryShardingEngine;
import org.apache.shardingsphere.core.constant.properties.ShardingPropertiesConstant;
import org.apache.shardingsphere.core.optimize.statement.impl.CommonSQLStatementContext;
import org.apache.shardingsphere.core.optimize.statement.SQLStatementContext;
import org.apache.shardingsphere.core.optimize.SQLStatementContextFactory;
import org.apache.shardingsphere.core.parse.sql.statement.SQLStatement;
import org.apache.shardingsphere.core.rewrite.RewriteEngine;
import org.apache.shardingsphere.core.route.RouteUnit;
import org.apache.shardingsphere.core.route.SQLRouteResult;
import org.apache.shardingsphere.core.route.SQLUnit;
import org.apache.shardingsphere.core.route.router.masterslave.MasterSlaveRouter;
import org.apache.shardingsphere.core.route.router.sharding.condition.ShardingCondition;
import org.apache.shardingsphere.core.route.router.sharding.condition.ShardingConditions;
import org.apache.shardingsphere.shardingproxy.backend.schema.LogicSchema;
import org.apache.shardingsphere.shardingproxy.backend.schema.impl.EncryptSchema;
import org.apache.shardingsphere.shardingproxy.backend.schema.impl.MasterSlaveSchema;
import org.apache.shardingsphere.shardingproxy.backend.schema.impl.ShardingSchema;
import org.apache.shardingsphere.shardingproxy.context.ShardingProxyContext;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;

/**
 * Executor wrapper for prepared statement.
 *
 * @author zhangliang
 * @author panjuan
 */
@RequiredArgsConstructor
public final class PreparedStatementExecutorWrapper implements JDBCExecutorWrapper {
    
    private static final ShardingProxyContext SHARDING_PROXY_CONTEXT = ShardingProxyContext.getInstance();
    
    private final LogicSchema logicSchema;
    
    private final List<Object> parameters;
    
    @Override
    public SQLRouteResult route(final String sql) {
        if (logicSchema instanceof ShardingSchema) {
            return doShardingRoute(sql);
        }
        if (logicSchema instanceof MasterSlaveSchema) {
            return doMasterSlaveRoute(sql);
        }
        return doEncryptRoute(sql);
    }
    
    private SQLRouteResult doShardingRoute(final String sql) {
        PreparedQueryShardingEngine shardingEngine = new PreparedQueryShardingEngine(
                sql, logicSchema.getShardingRule(), ShardingProxyContext.getInstance().getShardingProperties(), logicSchema.getMetaData(), logicSchema.getParseEngine());
        return shardingEngine.shard(sql, parameters);
    }
    
    private SQLRouteResult doMasterSlaveRoute(final String sql) {
        SQLStatement sqlStatement = logicSchema.getParseEngine().parse(sql, true);
        CommonSQLStatementContext sqlStatementContext = new CommonSQLStatementContext(sqlStatement);
        RewriteEngine rewriteEngine = new RewriteEngine(((MasterSlaveSchema) logicSchema).getMasterSlaveRule(), sqlStatementContext, sql);
        String rewriteSQL = rewriteEngine.generateSQL().getSql();
        SQLRouteResult result = new SQLRouteResult(sqlStatementContext, new ShardingConditions(Collections.<ShardingCondition>emptyList()));
        for (String each : new MasterSlaveRouter(((MasterSlaveSchema) logicSchema).getMasterSlaveRule(), logicSchema.getParseEngine(),
                SHARDING_PROXY_CONTEXT.getShardingProperties().<Boolean>getValue(ShardingPropertiesConstant.SQL_SHOW)).route(rewriteSQL, true)) {
            result.getRouteUnits().add(new RouteUnit(each, new SQLUnit(rewriteSQL, parameters)));
        }
        return result;
    }
    
    @SuppressWarnings("unchecked")
    private SQLRouteResult doEncryptRoute(final String sql) {
        EncryptSchema encryptSchema = (EncryptSchema) logicSchema;
        SQLStatement sqlStatement = encryptSchema.getParseEngine().parse(sql, true);
        SQLStatementContext sqlStatementContext = SQLStatementContextFactory.newInstance(logicSchema.getMetaData().getTables(), sql, parameters, sqlStatement);
        RewriteEngine rewriteEngine = new RewriteEngine(encryptSchema.getEncryptRule(), logicSchema.getMetaData().getTables(), 
                sqlStatementContext, sql, parameters, ShardingProxyContext.getInstance().getShardingProperties().<Boolean>getValue(ShardingPropertiesConstant.QUERY_WITH_CIPHER_COLUMN));
        SQLRouteResult result = new SQLRouteResult(sqlStatementContext, new ShardingConditions(Collections.<ShardingCondition>emptyList()));
        result.getRouteUnits().add(new RouteUnit(logicSchema.getDataSources().keySet().iterator().next(), new SQLUnit(rewriteEngine.generateSQL().getSql(), parameters)));
        return result;
    }
    
    @Override
    public Statement createStatement(final Connection connection, final SQLUnit sqlUnit, final boolean isReturnGeneratedKeys) throws SQLException {
        PreparedStatement result = isReturnGeneratedKeys ? connection.prepareStatement(sqlUnit.getSql(), Statement.RETURN_GENERATED_KEYS) : connection.prepareStatement(sqlUnit.getSql());
        for (int i = 0; i < sqlUnit.getParameters().size(); i++) {
            result.setObject(i + 1, sqlUnit.getParameters().get(i));
        }
        return result;
    }
    
    @Override
    public boolean executeSQL(final Statement statement, final String sql, final boolean isReturnGeneratedKeys) throws SQLException {
        return ((PreparedStatement) statement).execute();
    }
}
