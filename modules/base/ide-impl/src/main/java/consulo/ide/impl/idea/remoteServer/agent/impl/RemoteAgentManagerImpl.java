package consulo.ide.impl.idea.remoteServer.agent.impl;

import consulo.annotation.component.ServiceImpl;
import consulo.ide.impl.idea.openapi.util.io.FileUtil;
import consulo.ide.impl.idea.remoteServer.agent.RemoteAgentManager;
import consulo.ide.impl.idea.remoteServer.agent.RemoteAgentProxyFactory;
import consulo.ide.impl.idea.util.PathUtil;
import consulo.remoteServer.agent.RemoteAgent;
import jakarta.inject.Singleton;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author michael.golubev
 */
@Singleton
@ServiceImpl
public class RemoteAgentManagerImpl extends RemoteAgentManager {

  private final RemoteAgentClassLoaderCache myClassLoaderCache = new RemoteAgentClassLoaderCache();

  @Override
  public <T extends RemoteAgent> T createAgent(RemoteAgentProxyFactory agentProxyFactory,
                                               List<File> instanceLibraries,
                                               List<Class<?>> commonJarClasses,
                                               String specificsRuntimeModuleName,
                                               String specificsBuildJarPath,
                                               Class<T> agentInterface,
                                               String agentClassName,
                                               Class<?> pluginClass) throws Exception {

    List<Class<?>> allCommonJarClasses = new ArrayList<Class<?>>();
    allCommonJarClasses.addAll(commonJarClasses);
    allCommonJarClasses.add(RemoteAgent.class);
    allCommonJarClasses.add(agentInterface);

    List<File> libraries = new ArrayList<File>();
    libraries.addAll(instanceLibraries);

    for (Class<?> clazz : allCommonJarClasses) {
      libraries.add(new File(PathUtil.getJarPathForClass(clazz)));
    }

    File plugin = new File(PathUtil.getJarPathForClass(pluginClass));
    String allPluginsDir = plugin.getParent();
    if (plugin.isDirectory()) {
      // runtime behavior
      File specificsModule = new File(allPluginsDir, specificsRuntimeModuleName);
      libraries.add(specificsModule);
    }
    else {
      // build behavior
      File specificsDir = new File(allPluginsDir, FileUtil.toSystemDependentName(specificsBuildJarPath));
      libraries.add(specificsDir);
    }

    return agentProxyFactory.createProxy(libraries, agentInterface, agentClassName);
  }

  public RemoteAgentProxyFactory createReflectiveThreadProxyFactory(ClassLoader callerClassLoader) {
    return new RemoteAgentReflectiveThreadProxyFactory(myClassLoaderCache, callerClassLoader);
  }
}
