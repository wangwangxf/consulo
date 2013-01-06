package org.jetbrains.idea.maven.plugins.api;

import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.PairProcessor;
import com.intellij.util.ProcessingContext;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.model.*;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Sergey Evdokimov
 */
public class MavenPluginParamInfo {

  private static final Logger LOG = Logger.getInstance(MavenPluginParamInfo.class);

  /**
   * This map contains descriptions of all plugins.
   */
  private static volatile Map<String, Map> myMap;

  public static Map<String, Map> getMap() {
    Map<String, Map> res = myMap;

    if (res == null) {
      res = new HashMap<String, Map>();

      for (MavenPluginDescriptor pluginDescriptor : MavenPluginDescriptor.EP_NAME.getExtensions()) {
        if (pluginDescriptor.params == null) continue;

        Pair<String, String> pluginId = MavenPluginDescriptor.parsePluginId(pluginDescriptor.mavenId);

        for (MavenPluginDescriptor.Param param : pluginDescriptor.params) {
          String[] paramPath = param.name.split("/");

          Map pluginsMap = res;

          for (int i = paramPath.length - 1; i >= 0; i--) {
            pluginsMap = getOrCreate(pluginsMap, paramPath[i]);
          }

          ParamInfo paramInfo = new ParamInfo(pluginDescriptor.getPluginDescriptor().getPluginClassLoader(), param);

          Map<String, ParamInfo> goalsMap = getOrCreate(pluginsMap, pluginId);

          ParamInfo oldValue = goalsMap.put(param.goal, paramInfo);
          if (oldValue != null) {
            LOG.error("Duplicated maven plugin parameter descriptor: "
                      + pluginId.first + ':' + pluginId.second + " -> "
                      + (param.goal != null ? "[" + param.goal + ']' : "") + param.name);
          }
        }
      }

      myMap = res;
    }

    return res;
  }

  @NotNull
  private static <K, V extends Map> V getOrCreate(Map map, K key) {
    Map res = (Map)map.get(key);
    if (res == null) {
      res = new HashMap();
      map.put(key, res);
    }

    return (V)res;
  }

  public static boolean isSimpleText(@NotNull XmlText paramValue) {
    PsiElement prevSibling = paramValue.getPrevSibling();
    if (!(prevSibling instanceof LeafPsiElement) || ((LeafPsiElement)prevSibling).getElementType() != XmlTokenType.XML_TAG_END) {
      return false;
    }

    PsiElement nextSibling = paramValue.getNextSibling();
    if (!(nextSibling instanceof LeafPsiElement) || ((LeafPsiElement)nextSibling).getElementType() != XmlTokenType.XML_END_TAG_START) {
      return false;
    }

    return true;
  }

  public static void processParamInfo(@NotNull XmlText paramValue, @NotNull PairProcessor<ParamInfo, MavenDomConfiguration> processor) {
    XmlTag paramTag = paramValue.getParentTag();
    if (paramTag == null) return;

    XmlTag configurationTag = paramTag;
    DomElement domElement;

    Map m = getMap().get(paramTag.getName());

    while (true) {
      if (m == null) return;

      configurationTag = configurationTag.getParentTag();
      if (configurationTag == null) return;

      String tagName = configurationTag.getName();
      if ("configuration".equals(tagName)) {
        domElement = DomManager.getDomManager(configurationTag.getProject()).getDomElement(configurationTag);
        if (domElement instanceof MavenDomConfiguration) {
          break;
        }

        if (domElement != null) return;
      }

      m = (Map)m.get(tagName);
    }

    Map<Pair<String, String>, Map<String, ParamInfo>> pluginsMap = m;

    MavenDomConfiguration domCfg = (MavenDomConfiguration)domElement;

    MavenDomPlugin domPlugin = domCfg.getParentOfType(MavenDomPlugin.class, true);
    if (domPlugin == null) return;

    String pluginGroupId = domPlugin.getGroupId().getStringValue();
    String pluginArtifactId = domPlugin.getArtifactId().getStringValue();

    Map<String, ParamInfo> goalsMap;

    if (pluginGroupId == null) {
      goalsMap = pluginsMap.get(Pair.create("org.apache.maven.plugins", pluginArtifactId));
      if (goalsMap == null) {
        goalsMap = pluginsMap.get(Pair.create("org.codehaus.mojo", pluginArtifactId));
      }
    }
    else {
      goalsMap = pluginsMap.get(Pair.create(pluginGroupId, pluginArtifactId));
    }

    if (goalsMap == null) return;

    DomElement parent = domCfg.getParent();
    if (parent instanceof MavenDomPluginExecution) {
      MavenDomGoals goals = ((MavenDomPluginExecution)parent).getGoals();
      for (MavenDomGoal goal : goals.getGoals()) {
        ParamInfo info = goalsMap.get(goal.getStringValue());
        if (info != null) {
          if (!processor.process(info, domCfg)) return;
        }
      }
    }

    ParamInfo defaultInfo = goalsMap.get(null);
    if (defaultInfo != null) {
      if (!processor.process(defaultInfo, domCfg)) return;
    }
  }

  public static class ParamInfo {
    private final ClassLoader myClassLoader;

    private final MavenPluginDescriptor.Param myParam;

    private volatile boolean myLanguageInitialized;
    private Language myLanguageInstance;

    private volatile MavenParamReferenceProvider myProviderInstance;

    private ParamInfo(ClassLoader classLoader, MavenPluginDescriptor.Param param) {
      myClassLoader = classLoader;
      myParam = param;
    }

    public Language getLanguage() {
      if (!myLanguageInitialized) {
        if (myParam.language != null) {
          myLanguageInstance = Language.findLanguageByID(myParam.language);
        }

        myLanguageInitialized = true;
      }

      return myLanguageInstance;
    }

    public String getLanguageInjectionPrefix() {
      return myParam.languageInjectionPrefix;
    }

    public String getLanguageInjectionSuffix() {
      return myParam.languageInjectionSuffix;
    }

    public MavenParamReferenceProvider getProviderInstance() {
      MavenParamReferenceProvider res = myProviderInstance;

      if (res == null) {
        if (myParam.refProvider == null) {
          return null;
        }

        Object instance;

        try {
          instance = myClassLoader.loadClass(myParam.refProvider).newInstance();
        }
        catch (Exception e) {
          throw new RuntimeException("Failed to create reference provider instance", e);
        }

        if (instance instanceof MavenParamReferenceProvider) {
          res = (MavenParamReferenceProvider)instance;
        }
        else {
          final PsiReferenceProvider psiReferenceProvider = (PsiReferenceProvider)instance;

          res = new MavenParamReferenceProvider() {
            @Override
            public PsiReference[] getReferencesByElement(@NotNull PsiElement element,
                                                         @NotNull MavenDomConfiguration domCfg,
                                                         @NotNull ProcessingContext context) {
              return psiReferenceProvider.getReferencesByElement(element, context);
            }
          };
        }

        myProviderInstance = res;
      }

      return res;
    }
  }

}