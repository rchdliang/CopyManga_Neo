import com.rchdliang.copymanga_neo.multiplatform.restore.VulkanRestoreBridge;
import com.rchdliang.copymanga_neo.multiplatform.ui.reader.DesktopVulkanLibrary;
import java.nio.ByteBuffer;
public final class VulkanBridgeProbe {
    public static void main(String[] args) {
        DesktopVulkanLibrary.INSTANCE.load();
        boolean software = args.length > 0 && args[0].equals("--allow-software");
        long handle = VulkanRestoreBridge.create(software);
        try {
            byte[] model = DesktopVulkanLibrary.INSTANCE.resource("reader-restore/restore_m.akv");
            VulkanRestoreBridge.loadModel(handle, 1, model);
            int w=23, h=17;
            ByteBuffer input=ByteBuffer.allocateDirect(w*h*4), output=ByteBuffer.allocateDirect(w*h*4);
            for(int y=0;y<h;y++)for(int x=0;x<w;x++) input.put((byte)(x*9)).put((byte)(y*13)).put((byte)(x+y)).put((byte)255);
            input.rewind();
            VulkanRestoreBridge.renderTile(handle,1,input,w,h,output,0,0);
            long sum=0;
            for(int i=0;i<w*h*4;i++){
                int v=output.get(i)&255;
                if(i%4==3 && v!=255)throw new AssertionError("Unexpected alpha");
                if(i%4!=3)sum+=v;
            }
            if(sum==0)throw new AssertionError("Empty output");
            System.out.println(VulkanRestoreBridge.deviceName(handle)+" "+w+"x"+h+" checksum="+sum);
        } finally { VulkanRestoreBridge.destroy(handle); }
    }
}
