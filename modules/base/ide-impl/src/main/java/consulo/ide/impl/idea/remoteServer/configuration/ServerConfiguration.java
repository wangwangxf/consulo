package consulo.ide.impl.idea.remoteServer.configuration;

import consulo.component.persist.PersistentStateComponent;

/**
 * @author nik
 */
public abstract class ServerConfiguration {
  public abstract PersistentStateComponent<?> getSerializer();
}
