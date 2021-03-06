/*
 * Copyright (c) 2010, 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package org.glassfish.concurrent.runtime.deployer;


import com.sun.enterprise.config.serverbeans.Application;
import com.sun.enterprise.config.serverbeans.Resource;
import com.sun.enterprise.config.serverbeans.Resources;
import org.glassfish.api.logging.LogHelper;
import org.glassfish.concurrent.LogFacade;
import org.glassfish.concurrent.config.ManagedExecutorService;
import org.glassfish.concurrent.runtime.ConcurrentRuntime;
import org.glassfish.resourcebase.resources.api.ResourceConflictException;
import org.glassfish.resourcebase.resources.api.ResourceDeployer;
import org.glassfish.resourcebase.resources.api.ResourceDeployerInfo;
import org.glassfish.resourcebase.resources.api.ResourceInfo;
import org.glassfish.resourcebase.resources.naming.ResourceNamingService;
import org.glassfish.resourcebase.resources.util.ResourceUtil;
import org.glassfish.resources.naming.SerializableObjectRefAddr;
import org.jvnet.hk2.annotations.Service;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javax.naming.NamingException;
import javax.naming.RefAddr;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
@ResourceDeployerInfo(ManagedExecutorService.class)
@Singleton
public class ManagedExecutorServiceDeployer implements ResourceDeployer {

    @Inject
    private ResourceNamingService namingService;

    @Inject
    ConcurrentRuntime concurrentRuntime;

    // logger for this deployer
    private static Logger _logger = LogFacade.getLogger();

    @Override
    public void deployResource(Object resource, String applicationName, String moduleName) throws Exception {
        ManagedExecutorService managedExecutorServiceRes = (ManagedExecutorService) resource;

        if (managedExecutorServiceRes == null) {
            _logger.log(Level.WARNING, LogFacade.DEPLOY_ERROR_NULL_CONFIG, "ManagedExecutorService");
            return;
        }

        String jndiName = managedExecutorServiceRes.getJndiName();

        if(_logger.isLoggable(Level.FINE)) {
            _logger.log(Level.FINE, "ManagedExecutorServiceDeployer.deployResource() : jndi-name ["+jndiName+"], ");
        }


        ResourceInfo resourceInfo = new ResourceInfo(managedExecutorServiceRes.getJndiName(), applicationName, moduleName);
        ManagedExecutorServiceConfig config = new ManagedExecutorServiceConfig(managedExecutorServiceRes);

        javax.naming.Reference ref= new  javax.naming.Reference(
                jakarta.enterprise.concurrent.ManagedExecutorService.class.getName(),
                "org.glassfish.concurrent.runtime.deployer.ConcurrentObjectFactory",
                null);
        RefAddr addr = new SerializableObjectRefAddr(ManagedExecutorServiceConfig.class.getName(), config);
        ref.add(addr);
        RefAddr resAddr = new SerializableObjectRefAddr(ResourceInfo.class.getName(), resourceInfo);
        ref.add(resAddr);

        try {
            // Publish the object ref
            namingService.publishObject(resourceInfo, ref, true);
        } catch (NamingException ex) {
            LogHelper.log(_logger, Level.SEVERE, LogFacade.UNABLE_TO_BIND_OBJECT, ex, "ManagedExecutorService", jndiName);
        }
    }

    @Override
    public void deployResource(Object resource) throws Exception {
        ManagedExecutorService managedExecutorServiceResource =
                (ManagedExecutorService) resource;
        ResourceInfo resourceInfo = ResourceUtil.getResourceInfo(managedExecutorServiceResource);
        deployResource(resource, resourceInfo.getApplicationName(), resourceInfo.getModuleName());
    }

    @Override
    public void undeployResource(Object resource) throws Exception {
        ManagedExecutorService managedExecutorServiceResource =
                (ManagedExecutorService) resource;
        ResourceInfo resourceInfo = ResourceUtil.getResourceInfo(managedExecutorServiceResource);
        undeployResource(resource, resourceInfo.getApplicationName(), resourceInfo.getModuleName());
    }

    @Override
    public void undeployResource(Object resource, String applicationName, String moduleName) throws Exception {
        ManagedExecutorService managedExecutorServiceRes = (ManagedExecutorService) resource;
        ResourceInfo resourceInfo = new ResourceInfo(managedExecutorServiceRes.getJndiName(), applicationName, moduleName);
        namingService.unpublishObject(resourceInfo, managedExecutorServiceRes.getJndiName());
        // stop the runtime object
        concurrentRuntime.shutdownManagedExecutorService(managedExecutorServiceRes.getJndiName());
    }

    @Override
    public void redeployResource(Object resource) throws Exception {
        undeployResource(resource);
        deployResource(resource);
    }

    @Override
    public void enableResource(Object resource) throws Exception {
        deployResource(resource);
    }

    @Override
    public void disableResource(Object resource) throws Exception {
        undeployResource(resource);
    }

    @Override
    public boolean handles(Object resource) {
        return resource instanceof ManagedExecutorService;
    }

    @Override
    public boolean supportsDynamicReconfiguration() {
        return false;
    }

    @Override
    public Class[] getProxyClassesForDynamicReconfiguration() {
        return new Class[0];
    }

    @Override
    public boolean canDeploy(boolean postApplicationDeployment, Collection<Resource> allResources, Resource resource) {
        if (handles(resource)) {
            if (!postApplicationDeployment) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void validatePreservedResource(Application oldApp, Application newApp, Resource resource, Resources allResources) throws ResourceConflictException {
        // do nothing
    }
}
