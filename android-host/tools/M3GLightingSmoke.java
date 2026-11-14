import javax.microedition.lcdui.Image;
import javax.microedition.m3g.*;

public final class M3GLightingSmoke {
    private static int checks;
    private static void check(boolean condition,String message) {
        if(!condition) throw new AssertionError(message);
        checks++;
    }

    private static int render(int[] normal,Light light,Material material) {
        Camera camera=new Camera();
        camera.setPerspective(60,1,0.1f,100);
        World world=new World();
        Background background=new Background();
        background.setColor(0);
        world.setBackground(background);
        world.addChild(camera);
        world.setActiveCamera(camera);

        VertexArray positions=new VertexArray(3,3,VertexArray.SHORT);
        positions.set(0,3,new short[]{-1,-1,-10,1,-1,-10,0,1,-10});
        VertexArray normals=new VertexArray(3,3,VertexArray.SHORT);
        normals.set(0,3,new short[]{
                (short)normal[0],(short)normal[1],(short)normal[2],
                (short)normal[0],(short)normal[1],(short)normal[2],
                (short)normal[0],(short)normal[1],(short)normal[2]});
        VertexBuffer vertices=new VertexBuffer();
        vertices.setPositions(positions,0.1f,null);
        vertices.setNormals(normals);
        vertices.setDefaultColor(0xffffffff);
        Appearance appearance=new Appearance();
        PolygonMode polygon=new PolygonMode();
        polygon.setCulling(PolygonMode.CULL_NONE);
        appearance.setPolygonMode(polygon);
        appearance.setMaterial(material);
        Mesh mesh=new Mesh(vertices,
                new IndexBuffer[]{new TriangleStripArray(new int[]{0,1,2},new int[]{3})},
                new Appearance[]{appearance});
        world.addChild(light);
        world.addChild(mesh);

        Image target=Image.createImage(64,64);
        Graphics3D graphics=new Graphics3D();
        graphics.bindTarget(target.getGraphics(),true,0);
        graphics.setViewport(0,0,64,64);
        graphics.render(world);
        graphics.releaseTarget();
        int[] pixels=new int[64*64];
        target.getRGB(pixels,0,64,0,0,64,64);
        return pixels[32*64+32]&0xffffff;
    }

    public static void main(String[] args) {
        Light defaults=new Light();
        check(defaults.getMode()==Light.DIRECTIONAL,"M3G Light default mode");
        check(defaults.getColor()==0xffffff&&defaults.getIntensity()==1f,"M3G Light default color/intensity");
        check(defaults.getConstantAttenuation()==1f,"M3G Light default attenuation");

        Material diffuse=new Material();
        diffuse.setColor(Material.AMBIENT,0);
        diffuse.setColor(Material.DIFFUSE,0xffffffff);
        Light nearOmni=new Light();
        nearOmni.setMode(Light.OMNI);
        nearOmni.setColor(0xffffff);
        nearOmni.setIntensity(1f);
        nearOmni.setAttenuation(1f,0f,0f);
        int facing=render(new int[]{0,0,32767},nearOmni,diffuse);

        Light behindOmni=new Light();
        behindOmni.setMode(Light.OMNI);
        behindOmni.setColor(0xffffff);
        behindOmni.setIntensity(1f);
        behindOmni.setAttenuation(1f,0f,0f);
        behindOmni.setTranslation(0,0,-20);
        int away=render(new int[]{0,0,32767},behindOmni,diffuse);
        check(facing>0xf0f0f0,"M3G omni light did not illuminate a facing surface: #"+Integer.toHexString(facing));
        check(away<0x101010,"M3G omni light illuminated the back side: #"+Integer.toHexString(away));

        Material ambientMaterial=new Material();
        ambientMaterial.setColor(Material.AMBIENT,0xffffff);
        ambientMaterial.setColor(Material.DIFFUSE,0xff000000);
        Light ambient=new Light();
        ambient.setMode(Light.AMBIENT);
        ambient.setColor(0xffffff);
        ambient.setIntensity(0.5f);
        int ambientPixel=render(new int[]{0,0,32767},ambient,ambientMaterial);
        int ambientRed=(ambientPixel>>>16)&255;
        check(ambientRed>=126&&ambientRed<=129,
                "Ambient material/light product was incorrect: #"+Integer.toHexString(ambientPixel));

        Material specularMaterial=new Material();
        specularMaterial.setColor(Material.AMBIENT,0);
        specularMaterial.setColor(Material.DIFFUSE,0xff000000);
        specularMaterial.setColor(Material.SPECULAR,0xffffff);
        specularMaterial.setShininess(10f);
        Light specularLight=new Light();
        specularLight.setMode(Light.OMNI);
        specularLight.setColor(0xffffff);
        specularLight.setIntensity(1f);
        specularLight.setAttenuation(1f,0f,0f);
        int highlight=render(new int[]{0,0,32767},specularLight,specularMaterial);
        check(highlight>0xe0e0e0,
                "M3G specular lighting did not produce a highlight: #"+Integer.toHexString(highlight));
        System.out.println("M3GLightingSmoke: "+checks+" checks passed; M3G defaults and ambient/omni lighting");
    }
}
