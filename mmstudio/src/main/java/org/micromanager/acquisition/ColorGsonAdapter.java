///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// COPYRIGHT:    University of California, San Francisco, 2026
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//

package org.micromanager.acquisition;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.awt.Color;
import java.lang.reflect.Type;

/**
 * Gson (de)serializer for {@link Color}.
 *
 * <p>Without this adapter, Gson falls back to reflecting into Color's private
 * fields, which the Java module system blocks starting with Java 17 unless
 * the JVM is launched with {@code --add-opens java.desktop/java.awt} (and
 * {@code java.awt.color}, since a populated {@code Color.cs} field pulls in
 * {@code ColorSpace}/{@code ICC_Profile} too).
 *
 * <p>The "value" key matches the name of Color's own packed-ARGB field, so
 * JSON written by older Micro-Manager versions (which relied on Gson's
 * reflective fallback) still deserializes correctly here.
 */
final class ColorGsonAdapter implements JsonSerializer<Color>, JsonDeserializer<Color> {

   @Override
   public JsonElement serialize(Color src, Type typeOfSrc, JsonSerializationContext context) {
      JsonObject jo = new JsonObject();
      jo.addProperty("value", src.getRGB());
      return jo;
   }

   @Override
   public Color deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
         throws JsonParseException {
      if (!json.isJsonObject() || !json.getAsJsonObject().has("value")) {
         throw new JsonParseException("Expected a JSON object with a \"value\" field for Color");
      }
      return new Color(json.getAsJsonObject().get("value").getAsInt(), true);
   }
}
