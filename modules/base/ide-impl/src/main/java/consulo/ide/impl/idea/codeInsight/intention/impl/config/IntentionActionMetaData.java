/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * @author cdr
 */
package consulo.ide.impl.idea.codeInsight.intention.impl.config;

import consulo.language.editor.intention.IntentionAction;
import consulo.ide.impl.idea.util.io.URLUtil;
import consulo.container.classloader.PluginClassLoader;
import consulo.logging.Logger;
import consulo.container.plugin.PluginId;
import consulo.virtualFileSystem.fileType.FileType;
import consulo.language.file.FileTypeManager;
import consulo.ide.impl.idea.util.ArrayUtil;
import consulo.ide.impl.idea.util.ObjectUtil;
import org.jetbrains.annotations.NonNls;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class IntentionActionMetaData {
  private static final Logger LOG = Logger.getInstance(IntentionActionMetaData.class);
  @Nonnull
  private final IntentionAction myAction;
  private final ClassLoader myIntentionLoader;
  private final String myDescriptionDirectoryName;
  @Nonnull
  public final String[] myCategory;

  private TextDescriptor[] myExampleUsagesBefore = null;
  private TextDescriptor[] myExampleUsagesAfter = null;
  private TextDescriptor myDescription = null;
  private URL myDirURL = null;

  @NonNls private static final String BEFORE_TEMPLATE_PREFIX = "before";
  @NonNls private static final String AFTER_TEMPLATE_PREFIX = "after";
  @NonNls static final String EXAMPLE_USAGE_URL_SUFFIX = ".template";
  @NonNls private static final String DESCRIPTION_FILE_NAME = "description.html";
  @NonNls private static final String INTENTION_DESCRIPTION_FOLDER = "intentionDescriptions";

  public IntentionActionMetaData(@Nonnull IntentionAction action,
                                 @Nullable ClassLoader loader,
                                 @Nonnull String[] category,
                                 @Nonnull String descriptionDirectoryName) {
    myAction = action;
    myIntentionLoader = loader;
    myCategory = category;
    myDescriptionDirectoryName = descriptionDirectoryName;
  }

  public IntentionActionMetaData(@Nonnull final IntentionAction action,
                                 @Nonnull final String[] category,
                                 final TextDescriptor description,
                                 final TextDescriptor[] exampleUsagesBefore,
                                 final TextDescriptor[] exampleUsagesAfter) {
    myAction = action;
    myCategory = category;
    myExampleUsagesBefore = exampleUsagesBefore;
    myExampleUsagesAfter = exampleUsagesAfter;
    myDescription = description;
    myIntentionLoader = null;
    myDescriptionDirectoryName = null;
  }

  public String toString() {
    return getFamily();
  }

  @Nonnull
  public TextDescriptor[] getExampleUsagesBefore() {
    if(myExampleUsagesBefore == null){
      try {
        myExampleUsagesBefore = retrieveURLs(getDirURL(), BEFORE_TEMPLATE_PREFIX, EXAMPLE_USAGE_URL_SUFFIX);
      }
      catch (MalformedURLException e) {
        LOG.error(e);
      }
    }
    return myExampleUsagesBefore;
  }

  @Nonnull
  public TextDescriptor[] getExampleUsagesAfter() {
      if(myExampleUsagesAfter == null){
      try {
        myExampleUsagesAfter = retrieveURLs(getDirURL(), AFTER_TEMPLATE_PREFIX, EXAMPLE_USAGE_URL_SUFFIX);
      }
      catch (MalformedURLException e) {
        LOG.error(e);
      }
    }
    return myExampleUsagesAfter;
  }

  @Nonnull
  public TextDescriptor getDescription() {
    if(myDescription == null){
      try {
        final URL dirURL = getDirURL();
        URL descriptionURL = new URL(dirURL.toExternalForm() + "/" + DESCRIPTION_FILE_NAME);
        myDescription = new ResourceTextDescriptor(descriptionURL);
      }
      catch (MalformedURLException e) {
        LOG.error(e);
      }
    }
    return myDescription;
  }

  @Nonnull
  private static TextDescriptor[] retrieveURLs(@Nonnull URL descriptionDirectory, @Nonnull String prefix, @Nonnull String suffix) throws MalformedURLException {
    List<TextDescriptor> urls = new ArrayList<TextDescriptor>();
    final FileType[] fileTypes = FileTypeManager.getInstance().getRegisteredFileTypes();
    for (FileType fileType : fileTypes) {
      final String[] extensions = FileTypeManager.getInstance().getAssociatedExtensions(fileType);
      for (String extension : extensions) {
        for (int i = 0; ; i++) {
          URL url = new URL(descriptionDirectory.toExternalForm() + "/" +
                            prefix + "." + extension + (i == 0 ? "" : Integer.toString(i)) +
                            suffix);
          try {
            InputStream inputStream = url.openStream();
            inputStream.close();
            urls.add(new ResourceTextDescriptor(url));
          }
          catch (IOException ioe) {
            break;
          }
        }
      }
    }
    if (urls.isEmpty()) {
      String[] children;
      Exception cause = null;
      try {
        URI uri = descriptionDirectory.toURI();
        children = uri.isOpaque()? null : ObjectUtil.notNull(new File(uri).list(), ArrayUtil.EMPTY_STRING_ARRAY);
      }
      catch (URISyntaxException | IllegalArgumentException e) {
        cause = e;
        children = null;
      }
      LOG.error("URLs not found for available file types and prefix: '"+prefix+"', suffix: '"+suffix+"';" +
                " in directory: '"+descriptionDirectory+ "'" + (children == null? "" : "; directory contents: "+ Arrays.asList(children)), cause);
      return new TextDescriptor[0];
    }
    return urls.toArray(new TextDescriptor[urls.size()]);
  }

  @Nullable
  private static URL getIntentionDescriptionDirURL(ClassLoader aClassLoader, String intentionFolderName) {
    final URL pageURL = aClassLoader.getResource(INTENTION_DESCRIPTION_FOLDER + "/" + intentionFolderName+"/"+ DESCRIPTION_FILE_NAME);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Path:"+"intentionDescriptions/" + intentionFolderName);
      LOG.debug("URL:"+pageURL);
    }
    if (pageURL != null) {
      try {
        final String url = pageURL.toExternalForm();
        return URLUtil.internProtocol(new URL(url.substring(0, url.lastIndexOf('/'))));
      }
      catch (MalformedURLException e) {
        LOG.error(e);
      }
    }
    return null;
  }

  private URL getDirURL() {
    if (myDirURL == null) {
      myDirURL = getIntentionDescriptionDirURL(myIntentionLoader, myDescriptionDirectoryName);
    }
    if (myDirURL == null) { //plugin compatibility
      myDirURL = getIntentionDescriptionDirURL(myIntentionLoader, getFamily());
    }
    LOG.assertTrue(myDirURL != null, "Intention Description Dir URL is null: " +
                                     getFamily() +"; "+myDescriptionDirectoryName + ", " + myIntentionLoader);
    return myDirURL;
  }

  @Nullable public PluginId getPluginId() {
    if (myIntentionLoader instanceof PluginClassLoader) {
      return ((PluginClassLoader)myIntentionLoader).getPluginId();
    }
    return null;
  }

  @Nonnull
  public String getFamily() {
    return myAction.getFamilyName();
  }

  @Nonnull
  public IntentionAction getAction() {
    return myAction;
  }
}
