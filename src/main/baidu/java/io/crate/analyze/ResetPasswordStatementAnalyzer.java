/*
 * Copyright (c) 2017 Baidu, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.crate.analyze;

import io.crate.exceptions.NoPermissionException;
import io.crate.sql.tree.DefaultTraversalVisitor;
import io.crate.sql.tree.Node;
import io.crate.sql.tree.ResetPassword;

import org.elasticsearch.authentication.AuthService;
import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.cluster.metadata.UserProperty;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.rest.RestStatus;

public class ResetPasswordStatementAnalyzer extends DefaultTraversalVisitor<ResetPasswordAnalyzedStatement, Analysis> {

    private ClusterService clusterService;
    
    @Inject
    public ResetPasswordStatementAnalyzer(ClusterService clusterService) {
        this.clusterService = clusterService;
    }

    @Override
    public ResetPasswordAnalyzedStatement visitResetPassword(ResetPassword node, Analysis context) {
        UserProperty currentOperateUser = context.parameterContext().userProperty();
        String fullUserName = node.getUsername();
        if (!currentOperateUser.getUsernameWithoutTenant().equalsIgnoreCase(UserProperty.ROOT_NAME)
                && UserProperty.haveTenantInfo(fullUserName)) {
            throw new NoPermissionException(RestStatus.FORBIDDEN.getStatus(), "only root user could set password by specifying the tenantid");
        }
        if (UserProperty.haveTenantInfo(fullUserName)) {
            fullUserName = clusterService.state().metaData().getUserMetadata()
                .getUserPropertyFromLoginUser(node.getUsername(), clusterService.state().metaData().getTenantMetadata())
                .getUsernameWithTenant();
        } else {
            fullUserName = UserProperty.generateUsernameWithTenant(fullUserName, currentOperateUser.getTenantId());
        }
        if (AuthService.compareUserPrivilege(currentOperateUser.getUsernameWithoutTenant(), fullUserName) < 0) {
            throw new NoPermissionException(RestStatus.FORBIDDEN.getStatus(), 
                    String.format("user [%s] have no permission to modify user[%s] password", 
                            currentOperateUser.getUsernameWithoutTenant(), node.getUsername()));
        }
        ResetPasswordAnalyzedStatement statement = new ResetPasswordAnalyzedStatement(fullUserName, node.getPassword());
        return statement;
    }

    public AnalyzedStatement analyze(Node node, Analysis analysis) {
        analysis.expectsAffectedRows(true);
        return process(node, analysis);
    }
}