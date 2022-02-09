/*
   Copyright 2021 Tobias Stadler

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
package co.elastic.apm.agent.wildfly_deployment;

import net.bytebuddy.asm.Advice;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.Phase;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.modules.Module;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

public class DeploymentUnitPhaseServiceAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExitStart(@Advice.FieldValue("phase") Phase phase, @Advice.FieldValue("deploymentUnit") DeploymentUnit deploymentUnit) {
        if (phase == Phase.FIRST_MODULE_USE) {
            Object elasticApmTracer = getElasticApmTracer();
            if (elasticApmTracer == null) {
                return;
            }

            Module module = deploymentUnit.getAttachment(Attachments.MODULE);

            ResourceRoot resourceRoot = deploymentUnit.getAttachment(Attachments.DEPLOYMENT_ROOT);
            Manifest manifest = resourceRoot.getAttachment(Attachments.MANIFEST);
            if (manifest == null) {
                return;
            }

            Attributes mainAttributes = manifest.getMainAttributes();
            if (mainAttributes == null || !mainAttributes.containsKey(Attributes.Name.IMPLEMENTATION_TITLE)) {
                return;
            }

            try {
                Class<?> elasticApmTracerClass = Class.forName("co.elastic.apm.agent.impl.ElasticApmTracer");
                Class<?> serviceInfoClass = Class.forName("co.elastic.apm.agent.configuration.ServiceInfo");
                Object serviceInfo = MethodHandles.publicLookup()
                        .findStatic(serviceInfoClass, "of", MethodType.methodType(serviceInfoClass, String.class, String.class))
                        .invoke(mainAttributes.getValue(Attributes.Name.IMPLEMENTATION_TITLE), mainAttributes.getValue(Attributes.Name.IMPLEMENTATION_VERSION));

                MethodHandles.publicLookup()
                        .findVirtual(elasticApmTracerClass, "overrideServiceInfoForClassLoader", MethodType.methodType(void.class, ClassLoader.class, serviceInfoClass))
                        .bindTo(elasticApmTracer)
                        .invoke(module.getClassLoader(), serviceInfo);
            } catch (Throwable ignored) {
            }
        }
    }

    private static Object getElasticApmTracer() {
        try {
            return MethodHandles.publicLookup()
                    .findStatic(Class.forName("co.elastic.apm.agent.impl.GlobalTracer"), "getTracerImpl", MethodType.methodType(Class.forName("co.elastic.apm.agent.impl.ElasticApmTracer")))
                    .invoke();
        } catch (Throwable ignored) {
            return null;
        }
    }
}
