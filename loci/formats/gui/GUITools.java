//
// GUITools.java
//

/*
LOCI Bio-Formats package for reading and converting biological file formats.
Copyright (C) 2005-@year@ Melissa Linkert, Curtis Rueden, Chris Allan,
Eric Kjellman and Brian Loranger.

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU Library General Public License as published by
the Free Software Foundation; either version 2 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Library General Public License for more details.

You should have received a copy of the GNU Library General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package loci.formats.gui;

import java.lang.reflect.InvocationTargetException;
import java.util.Vector;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileFilter;
import loci.formats.*;

/**
 * A utility class for working with graphical user interfaces.
 *
 * @author Curtis Rueden ctrueden at wisc.edu
 */
public final class GUITools {

  // -- Static fields --

  //...

  // -- Constructor --

  private GUITools() { }

  // -- File chooser --

  public static FileFilter[] buildFileFilters(IFormatHandler handler) {
    FileFilter[] ff = null;

    // unwrap reader
    while (true) {
      if (handler instanceof ReaderWrapper) {
        handler = ((ReaderWrapper) handler).getReader();
      }
      else if (handler instanceof FileStitcher) {
        handler = ((FileStitcher) handler).getReader();
      }
      else break;
    }

    // handle special cases of ImageReader and ImageWriter
    if (handler instanceof ImageReader) {
      IFormatReader[] readers = ((ImageReader) handler).getReaders();
      Vector v = new Vector();
      for (int i=0; i<readers.length; i++) {
        v.add(new FormatFileFilter(readers[i]));
      }
      ff = ComboFileFilter.sortFilters(v);
    }
    else if (handler instanceof ImageWriter) {
      IFormatWriter[] writers = ((ImageWriter) handler).getWriters();
      Vector v = new Vector();
      for (int i=0; i<writers.length; i++) {
        String[] suffixes = writers[i].getSuffixes();
        String format = writers[i].getFormat();
        v.add(new ExtensionFileFilter(suffixes, format));
      }
      ff = ComboFileFilter.sortFilters(v);
    }

    // handle default reader and writer cases
    else if (handler instanceof IFormatReader) {
      IFormatReader reader = (IFormatReader) handler;
      ff = new FileFilter[] {new FormatFileFilter(reader)};
    }
    else {
      String[] suffixes = handler.getSuffixes();
      String format = handler.getFormat();
      ff = new FileFilter[] {new ExtensionFileFilter(suffixes, format)};
    }
    return ff;
  }

  public static JFileChooser buildFileChooser(IFormatHandler handler) {
    return buildFileChooser(buildFileFilters(handler));
  }

  /**
   * Builds a file chooser with the given file filters,
   * as well as an "All supported file types" combo filter.
   */
  public static JFileChooser buildFileChooser(final FileFilter[] filters) {
    // NB: must construct JFileChooser in the
    // AWT worker thread, to avoid deadlocks
    final JFileChooser[] jfc = new JFileChooser[1];
    Runnable r = new Runnable() {
      public void run() {
        JFileChooser fc = new JFileChooser(System.getProperty("user.dir"));
        FileFilter[] ff = ComboFileFilter.sortFilters(filters);
        FileFilter combo = null;
        if (ff.length > 1) {
          // By default, some readers might need to open a file to determine
          // if it is the proper type, when the extension alone isn't enough
          // to distinguish.
          //
          // We want to disable that behavior for the "All supported file
          // types" combination filter, because otherwise it is too slow.
          //
          // Also, most of the formats that do this are TIFF-based, and the
          // TIFF reader will already green-light anything with .tif
          // extension, making more thorough checks redundant.
          combo = new ComboFileFilter(ff, "All supported file types", false);
          fc.addChoosableFileFilter(combo);
        }
        for (int i=0; i<ff.length; i++) fc.addChoosableFileFilter(ff[i]);
        if (combo != null) fc.setFileFilter(combo);
        jfc[0] = fc;
      }
    };
    if (Thread.currentThread().getName().startsWith("AWT-EventQueue")) {
      // current thread is the AWT event queue thread; just execute the code
      r.run();
    }
    else {
      // execute the code with the AWT event thread
      try {
        SwingUtilities.invokeAndWait(r);
      }
      catch (InterruptedException exc) { return null; }
      catch (InvocationTargetException exc) { return null; }
    }
    return jfc[0];
  }

}
