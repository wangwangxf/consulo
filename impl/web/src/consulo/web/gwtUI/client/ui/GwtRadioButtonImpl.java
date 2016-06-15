/*
 * Copyright 2013-2016 must-be.org
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
package consulo.web.gwtUI.client.ui;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.user.client.ui.RadioButton;
import consulo.web.gwtUI.client.WebSocketProxy;
import consulo.web.gwtUI.shared.UIClientEvent;
import consulo.web.gwtUI.shared.UIClientEventType;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * @author VISTALL
 * @since 14-Jun-16
 */
public class GwtRadioButtonImpl extends RadioButton implements InternalGwtComponentWithListeners {
  public GwtRadioButtonImpl() {
    super(null);
  }

  @Override
  public void setupListeners(final WebSocketProxy proxy, final long componentId) {
    addValueChangeHandler(new ValueChangeHandler<Boolean>() {
      @Override
      public void onValueChange(final ValueChangeEvent<Boolean> event) {
        proxy.send(UIClientEventType.invokeEvent, new WebSocketProxy.Consumer<UIClientEvent>() {
          @Override
          public void consume(UIClientEvent clientEvent) {
            Map<String, String> vars = new HashMap<String, String>();
            vars.put("type", "select");
            vars.put("componentId", String.valueOf(componentId));
            vars.put("selected", String.valueOf(event.getValue()));

            clientEvent.setVariables(vars);
          }
        });
      }
    });
  }

  @Override
  public void updateState(@NotNull Map<String, String> map) {
    final String text = map.get("text");
    if(text != null) {
      setText(text);
    }

    setValue(DefaultVariables.parseBoolAsTrue(map, "selected"));
  }
}
