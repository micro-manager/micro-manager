/*
 * Created on Jul 5, 2005
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
import ij.IJ;
import ij.plugin.*;

/**
 * @author nenada
 *
 * TODO To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
public class DumpEnv_ implements PlugIn{
    {
    }

    public void run(String arg) {
        // dump to ImageJ
        IJ.write("TEMP=" + System.getProperty("java.io.tmpdir"));
        IJ.write("PATH=" + System.getProperty("java.library.path"));
        IJ.write("java.class.path=" + System.getProperty("java.class.path"));
        IJ.write("java.library.path=" + System.getProperty("java.library.path")+"}");
        
        // dump to console
        System.out.println("TEMP=" + System.getProperty("java.io.tmpdir"));
        System.out.println("PATH=" + System.getProperty("java.library.path"));
        System.out.println("java.class.path=" + System.getProperty("java.class.path"));
        System.out.println("java.library.path=" + System.getProperty("java.library.path")+"}");
        
        IJ.register(DumpEnv_.class);
        //System.loadLibrary("MMCoreJ_wrap");
        //System.load("c:/projects/MicroManage/bin/MMCoreJ_wrap.dll");
    }
}
