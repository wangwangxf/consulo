// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.DaemonBundle;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import javax.annotation.Nonnull;

public class DaemonTooltipsUtil {
  @Nonnull
  public static String getWrappedTooltip(String message, String shortName, String shortcutText, boolean showToolDescription) {
    String link = "";
    if (showToolDescription) {
      //noinspection HardCodedStringLiteral
      link = " <a " +
             "href=\"#inspection/" +
             shortName +
             "\"" +
             (UIUtil.isUnderDarcula() ? " color=\"7AB4C9\" " : "") +
             ">" +
             DaemonBundle.message("inspection.extended.description") +
             "</a> " +
             shortcutText;
    }
    return XmlStringUtil.wrapInHtml((message.startsWith("<html>") ? XmlStringUtil.stripHtml(message) : XmlStringUtil.escapeString(message)) + link);
  }
}
