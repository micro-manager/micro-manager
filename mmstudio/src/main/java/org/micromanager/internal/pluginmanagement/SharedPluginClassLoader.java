///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//COPYRIGHT:     University of California, San Francisco, 2006-2013
//LICENSE:       This file is distributed under the BSD license.
//               License text is included with the source distribution.
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//

package org.micromanager.internal.pluginmanagement;

import java.net.URL;
import java.net.URLClassLoader;

/**
 * A single class loader shared by all Micro-Manager plugins.
 *
 * <p>Historically each plugin  got its own {@link URLClassLoader}, which meant plugins
 *  could not see each other's classes, and code on Micro-Manager's class
 * loader could not see plugin classes at all (a parent class loader never sees its children).
 *
 * <p>This loader instead holds the JARs from <em>all</em> plugin directories, with
 * Micro-Manager's own class loader as its parent. Because every plugin is loaded through this one
 * loader, plugins can see each other, and a single class loader is enough to give the
 * Pycro-Manager / ZMQ bridge visibility of every plugin class.
 *
 * <p>We deliberately create our own {@link URLClassLoader} rather than reflectively adding URLs
 * to Micro-Manager's existing loader: the latter relies on the application class loader being a
 * {@code URLClassLoader} and on bypassing access checks, both of which break on Java 9+. A
 * {@code URLClassLoader} we instantiate ourselves remains valid on all Java versions, and being a
 * {@code URLClassLoader} keeps it compatible with the ZMQ bridge's package enumeration.
 *
 * <p>Plugin discovery (scanning for SciJava {@code @Plugin} index resources) is <em>not</em> done
 * on this loader; {@link PluginFinder} runs discovery on a throwaway loader scoped to the JARs
 * being scanned so it does not pick up index files from every JAR on the parent class path. This
 * loader only needs to expose {@link #addURL} so that the discovered JARs accumulate on it.
 */
public final class SharedPluginClassLoader extends URLClassLoader {

   public SharedPluginClassLoader(ClassLoader parent) {
      super(new URL[0], parent);
   }

   /**
    * Add a plugin JAR (or other URL) to this loader's search path.
    *
    * <p>{@link URLClassLoader#addURL} is protected; this method exposes it within the package so
    * that {@link PluginFinder} can accumulate JARs from every scanned directory.
    *
    * @param url the URL to add
    */
   @Override
   public void addURL(URL url) {
      super.addURL(url);
   }
}
