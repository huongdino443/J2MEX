import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import javax.microedition.m3g.*;
import org.recompile.mobile.*;

public final class M3GLoaderSmoke {
    private static int checks;
    private static void check(boolean ok,String message) {
        if(!ok) throw new AssertionError(message); checks++;
    }
    public static void main(String[] args) throws Exception {
        MobilePlatform platform=new MobilePlatform(240,320);
        Mobile.setPlatform(platform);
        platform.dataPath=Files.createTempDirectory("m3g-loader-smoke-").toString()+File.separator;
        platform.loader=new MIDletLoader(new URL[]{new File(args[0]).toURI().toURL()});
        for(String resource:new String[]{"data.001","data.002"}) {
            byte[] data;
            try(ZipFile jar=new ZipFile(args[0]); InputStream in=jar.getInputStream(jar.getEntry(resource))) {
                ByteArrayOutputStream bytes=new ByteArrayOutputStream(); byte[] buffer=new byte[8192]; int n;
                while((n=in.read(buffer))!=-1) bytes.write(buffer,0,n); data=bytes.toByteArray();
            }
            // This pair swap is the original game's resource decoder, not part of Loader.
            for(int i=0;i<40;i+=2) { byte t=data[i]; data[i]=data[i+1]; data[i+1]=t; }
            Object3D[] roots=Loader.load(data,0);
            World world=null;
            for(Object3D root:roots) if(root instanceof World) world=(World)root;
            check(world!=null,resource+": no World root");
            check(world.getChildCount()>0,resource+": no children");
            check(world.getActiveCamera()!=null,resource+": no active camera");
            if(args.length>1) render(world,args[1]+"/v47-"+resource+".png");
            int meshes=0;
            for(int id=1;id<=100;id++) if(world.find(id) instanceof Mesh) meshes++;
            check(meshes>0,resource+": find(userID) did not resolve Mesh objects");
            World copy=(World)world.duplicate();
            check(copy!=world&&copy.getChild(0)!=world.getChild(0),resource+": duplicate aliased child nodes");
            check(copy.getActiveCamera()!=world.getActiveCamera(),resource+": duplicate kept the original camera");
            Transform relative=new Transform();
            check(copy.getActiveCamera().getTransformTo(copy,relative),resource+": camera transformTo World failed");
            check(world.find(Integer.MAX_VALUE)==null,resource+": nonexistent user ID did not return null");
            byte[] prefixed=new byte[data.length+7]; System.arraycopy(data,0,prefixed,7,data.length);
            check(Loader.load(prefixed,7).length==roots.length,"nonzero offset");
            byte[] bad=data.clone(); bad[bad.length-1]^=1;
            try { Loader.load(bad,0); throw new AssertionError("bad checksum accepted"); }
            catch(IOException expected) { checks++; }
            try { Loader.load(Arrays.copyOf(data,data.length-1),0); throw new AssertionError("truncation accepted"); }
            catch(IOException expected) { checks++; }
            System.out.println(resource+": "+roots.length+" roots, "+world.getChildCount()+" children, "+meshes+" named meshes");
        }
        Material material=new Material(); material.setColor(Material.AMBIENT,0x123456); material.setColor(Material.DIFFUSE,0xffabcdef);
        check(material.getColor(Material.AMBIENT)==0x123456&&material.getColor(Material.DIFFUSE)==0xffabcdef,
                "material color targets overwrite each other");
        KeyframeSequence sequence=new KeyframeSequence(2,3,KeyframeSequence.LINEAR);
        sequence.setDuration(1000); sequence.setKeyframe(0,0,new float[]{0,0,0});
        sequence.setKeyframe(1,1000,new float[]{10,20,30});
        AnimationController controller=new AnimationController(); controller.setPosition(0,0);
        AnimationTrack track=new AnimationTrack(sequence,AnimationTrack.TRANSLATION); track.setController(controller);
        Group animated=new Group(); animated.addAnimationTrack(track); animated.animate(500); float[] xyz=new float[3];
        animated.getTranslation(xyz); check(Math.abs(xyz[0]-5)<0.001f&&Math.abs(xyz[2]-15)<0.001f,"linear animation");
        controller.setSpeed(2,500); check(Math.abs(controller.getPosition(750)-1000)<0.001f,"animation speed discontinuity");
        Group positioned=new Group(); positioned.setTranslation(10,20,30); positioned.setOrientation(0,0,0,0);
        positioned.setOrientation(90,0,1,0); positioned.getTranslation(xyz);
        check(xyz[0]==10&&xyz[1]==20&&xyz[2]==30,"orientation moved the node's translation");
        Transform t=new Transform(); t.postTranslate(2,3,4);
        float[] point={1,2,3,1, 1,2,3,0}; t.transform(point);
        check(point[0]==3&&point[1]==5&&point[2]==7&&point[3]==1,"homogeneous point transformation");
        check(point[4]==1&&point[5]==2&&point[6]==3&&point[7]==0,"translation affected a direction vector");
        System.out.println("M3GLoaderSmoke: "+checks+" checks passed");
    }
    private static void render(World world,String path) throws Exception {
        // These Worlds are model libraries, not the game's playable scene.
        // Frame an actual named Mesh instead of assuming the export camera is a useful view.
        Mesh mesh=null;
        for(int id=1;id<=100&&mesh==null;id++) if(world.find(id) instanceof Mesh) mesh=(Mesh)world.find(id);
        if(mesh==null) throw new AssertionError("No named Mesh to render");
        VertexBuffer vertices=mesh.getVertexBuffer();
        float[] sb=new float[4]; VertexArray positions=vertices.getPositions(sb);
        int n=positions.getVertexCount(),components=positions.getComponentCount();
        short[] values=new short[n*components];
        if(positions.getComponentType()==VertexArray.SHORT) positions.get(0,n,values);
        else { byte[] bytes=new byte[values.length]; positions.get(0,n,bytes); for(int i=0;i<values.length;i++) values[i]=bytes[i]; }
        float[] min={Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY};
        float[] max={Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY};
        for(int i=0;i<n;i++) for(int axis=0;axis<3;axis++) {
            float p=values[i*components+axis]*sb[0]+sb[axis+1];
            min[axis]=Math.min(min[axis],p); max[axis]=Math.max(max[axis],p);
        }
        float radius=Math.max(0.1f,Math.max(max[0]-min[0],Math.max(max[1]-min[1],max[2]-min[2]))/2);
        Camera camera=new Camera(); camera.setPerspective(45,240f/320, radius/100, radius*20);
        Transform cameraWorld=new Transform();
        float zx=0.4f,zy=0.3f,zz=(float)Math.sqrt(0.75);
        float length=(float)Math.sqrt(zx*zx+zz*zz),xx=zz/length,xz=-zx/length;
        float yx=zy*xz,yy=zz*xx-zx*xz,yz=-zy*xx;
        cameraWorld.set(new float[]{
            xx,yx,zx,(min[0]+max[0])/2+radius*4*zx,
            0,yy,zy,(min[1]+max[1])/2+radius*4*zy,
            xz,yz,zz,(min[2]+max[2])/2+radius*4*zz,
            0,0,0,1});
        javax.microedition.lcdui.Image target=javax.microedition.lcdui.Image.createImage(240,320);
        Graphics3D graphics=new Graphics3D();
        graphics.bindTarget(target.getGraphics(),true,Graphics3D.OVERWRITE);
        graphics.setViewport(0,0,240,320);
        Background background=new Background(); background.setColor(0xff000000); graphics.clear(background);
        graphics.setCamera(camera,cameraWorld);
        // data.002 is an appearance-template library; the game replaces its placeholder
        // images after loading. Test its geometry with a diagnostic material instead.
        Appearance diagnostic=new Appearance(); PolygonMode mode=new PolygonMode(); mode.setCulling(PolygonMode.CULL_NONE);
        diagnostic.setPolygonMode(mode); Material material=new Material(); material.setColor(Material.DIFFUSE,0xff40a0ff);
        diagnostic.setMaterial(material);
        for(int i=0;i<mesh.getSubmeshCount();i++)
            graphics.render(vertices,mesh.getIndexBuffer(i),diagnostic,new Transform());
        graphics.releaseTarget();
        int[] pixels=new int[240*320]; target.getRGB(pixels,0,240,0,0,240,320);
        java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(240,320,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0,0,240,320,pixels,0,240);
        javax.imageio.ImageIO.write(image,"png",new File(path));
        Set<Integer> colors=new HashSet<Integer>(); int filled=0;
        for(int p:pixels) { colors.add(p); if((p&0xffffff)!=0) filled++; }
        check(colors.size()>1&&filled>32,"loaded named Mesh did not rasterize: "+path+" colors="+colors.size());
        System.out.println("BINARY_MESH_RENDER "+path+" vertices="+n+" colors="+colors.size()+" pixels="+filled);
    }
}
