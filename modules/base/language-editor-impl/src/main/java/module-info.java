/**
 * @author VISTALL
 * @since 23-Mar-22
 */
module consulo.language.editor.impl {
  // TODO remove this dependency in future
  requires java.desktop;
  requires forms.rt;

  requires transitive consulo.language.editor.api;
  requires transitive consulo.file.template.api;
  requires consulo.language.inject.impl;
  requires consulo.external.service.api;
  requires consulo.application.impl;
  requires consulo.project.impl;

  exports consulo.language.editor.impl.action;
  exports consulo.language.editor.impl.intention;
  exports consulo.language.editor.impl.highlight;
  exports consulo.language.editor.impl.inspection.reference;

  exports consulo.language.editor.impl.internal.daemon to consulo.ide.impl;
  exports consulo.language.editor.impl.internal.completion to consulo.ide.impl, consulo.desktop.awt.ide.impl;
  exports consulo.language.editor.impl.internal.intention to consulo.ide.impl;
  exports consulo.language.editor.impl.internal.parser to consulo.ide.impl;
  exports consulo.language.editor.impl.internal.inspection.scheme to consulo.ide.impl;
  exports consulo.language.editor.impl.internal.inspection to consulo.ide.impl;
  exports consulo.language.editor.impl.internal.psi.path to consulo.ide.impl;
  exports consulo.language.editor.impl.internal.template to consulo.ide.impl;
  exports consulo.language.editor.impl.internal.highlight to consulo.ide.impl;
  exports consulo.language.editor.impl.internal.rawHighlight to consulo.ide.impl;
  exports consulo.language.editor.impl.internal.markup to consulo.ide.impl, consulo.desktop.swt.ide.impl;
  exports consulo.language.editor.impl.internal.hint to consulo.ide.impl;

  opens consulo.language.editor.impl.internal.inspection.scheme to consulo.util.xml.serializer, consulo.component.impl;
}