package javax.microedition.m3g;

import java.io.IOException;
import java.util.*;
import java.util.zip.*;

/** JSR-184 1.0 sections and the static scene objects used by the first binary-model games. */
final class M3GLoader {
    private static final byte[] MAGIC = {(byte)0xab,74,83,82,49,56,52,(byte)0xbb,13,10,26,10};
    private final List<Object3D> table = new ArrayList<Object3D>();
    private final List<Object3D> local = new ArrayList<Object3D>();
    private final Set<Object3D> referenced = Collections.newSetFromMap(new IdentityHashMap<Object3D,Boolean>());
    private final String source;
    private boolean header;

    private static final class SkinTransformData {
        final Node bone;
        final int firstVertex, numVertices, weight;
        SkinTransformData(Node bone, int firstVertex, int numVertices, int weight) {
            this.bone = bone;
            this.firstVertex = firstVertex;
            this.numVertices = numVertices;
            this.weight = weight;
        }
    }

    private M3GLoader(String source) { this.source = source; table.add(null); }

    static Object3D[] decode(byte[] bytes, int offset, String source) throws IOException {
        if (bytes.length - offset > M3GInput.MAX_BYTES) throw new IOException("M3G file too large");
        if (bytes.length - offset >= 8 && (bytes[offset] & 255) == 137 && bytes[offset+1] == 80
                && bytes[offset+2] == 78 && bytes[offset+3] == 71) {
            try {
                javax.microedition.lcdui.Image image = javax.microedition.lcdui.Image.createImage(
                        bytes, offset, bytes.length - offset);
                return new Object3D[]{new Image2D(Image2D.RGBA, image)};
            } catch (RuntimeException error) { throw new IOException("Invalid M3G PNG image", error); }
        }
        M3GInput input = new M3GInput(bytes, offset, bytes.length - offset);
        for (byte expected : MAGIC) if (input.u8() != (expected & 255)) throw new IOException("Invalid M3G signature");
        M3GLoader loader = new M3GLoader(source);
        int totalInflated = 0;
        try {
            while (input.left() > 0) {
                int start = input.position, compression = input.u8();
                int size = input.i32(), rawSize = input.i32();
                if (size < 13 || rawSize < 0 || rawSize > M3GInput.MAX_BYTES - totalInflated)
                    throw new IOException("Invalid/excessive M3G section size");
                totalInflated += rawSize;
                M3GInput body = input.slice(size - 13);
                long checksum = input.i32() & 0xffffffffL;
                Adler32 adler = new Adler32(); adler.update(bytes, start, size - 4);
                if (adler.getValue() != checksum) throw new IOException("M3G section checksum mismatch");
                if (!loader.header && compression != 0) throw new IOException("Compressed M3G header");
                if (compression == 1) {
                    Inflater inflater = new Inflater();
                    byte[] raw = new byte[rawSize];
                    try {
                        inflater.setInput(body.bytes, body.position, body.left());
                        int written = 0;
                        while (written < raw.length) {
                            int n = inflater.inflate(raw, written, raw.length - written);
                            if (n == 0) break;
                            written += n;
                        }
                        if (written != raw.length || !inflater.finished() || inflater.getRemaining() != 0)
                            throw new IOException("Invalid M3G compressed section");
                    } catch (DataFormatException error) { throw new IOException("Invalid M3G zlib data", error); }
                    finally { inflater.end(); }
                    body = new M3GInput(raw, 0, raw.length);
                } else if (compression != 0 || body.left() != rawSize)
                    throw new IOException("Unsupported M3G compression or wrong section length");
                while (body.left() > 0) {
                    if (loader.table.size() >= 65536) throw new IOException("Too many M3G objects");
                    int type = body.u8(), length = body.i32();
                    M3GInput object = body.slice(length);
                    if (type == 0) {
                        if (loader.header || loader.table.size() != 1 || body.left() != 0)
                            throw new IOException("Invalid M3G header placement");
                        if (object.u8() != 1 || object.u8() != 0) throw new IOException("Unsupported M3G version");
                        object.bool();
                        if (object.i32() != bytes.length - offset) throw new IOException("Wrong M3G file size");
                        object.i32(); object.string(); loader.header = true;
                        loader.table.add(null); // Header has object index 1, but is not an Object3D.
                    } else {
                        if (!loader.header) throw new IOException("Missing M3G header");
                        Object3D value = type == 255 ? loader.external(object.string()) : loader.object(type, object);
                        loader.table.add(value);
                        if (type != 255) loader.local.add(value);
                    }
                    if (object.left() != 0) throw new IOException("Trailing bytes in M3G object type " + type);
                }
            }
        } catch (RuntimeException error) {
            throw new IOException("Invalid M3G object data", error);
        }
        if (!loader.header) throw new IOException("Missing M3G header");
        List<Object3D> roots = new ArrayList<Object3D>();
        for (Object3D value : loader.local) if (!loader.referenced.contains(value)) roots.add(value);
        return roots.toArray(new Object3D[roots.size()]);
    }

    private Object3D external(String uri) throws IOException {
        String resolved;
        try { resolved = new java.net.URI(source).resolve(uri).normalize().toString(); }
        catch (java.net.URISyntaxException error) { throw new IOException("Invalid M3G external URI", error); }
        Object3D[] values = Loader.load(resolved);
        if (values.length != 1) throw new IOException("M3G external reference must have one root: " + resolved);
        return values[0];
    }
    private <T> T ref(M3GInput in, Class<T> type) throws IOException {
        int index = in.i32();
        if (index == 0) return null;
        if (index < 2 || index >= table.size()) throw new IOException("Invalid/forward M3G object reference " + index);
        Object3D value = table.get(index);
        if (!type.isInstance(value)) throw new IOException("Wrong M3G reference type at " + index);
        referenced.add(value); return type.cast(value);
    }
    private Object3D object(int type, M3GInput in) throws IOException {
        Base base = new Base(in);
        TransformData transform = null;
        NodeState node = null;
        boolean isNode = type == 5 || type == 9 || type == 12 || type == 14
                || type == 15 || type == 16 || type == 18 || type == 22;
        if (isNode || type == 17) transform = transform(in);
        if (isNode) node = new NodeState(in);
        Object3D value;
        List<SkinTransformData> skinTransforms = null;
        switch (type) {
        case 1: {
            AnimationController c = new AnimationController();
            float speed=in.f32(), weight=in.f32(); int start=in.i32(), end=in.i32();
            float sequence=in.f32(); int world=in.i32();
            c.setActiveInterval(start,end); c.setPosition(sequence,world); c.setSpeed(speed,world); c.setWeight(weight);
            value=c; break;
        }
        case 2: {
            KeyframeSequence sequence=ref(in,KeyframeSequence.class);
            AnimationController controller=ref(in,AnimationController.class);
            AnimationTrack track=new AnimationTrack(sequence,in.i32()); track.setController(controller); value=track; break;
        }
        case 3: {
            Appearance a=new Appearance(); a.setLayer((byte)in.u8());
            a.setCompositingMode(ref(in,CompositingMode.class)); a.setFog(ref(in,Fog.class));
            a.setPolygonMode(ref(in,PolygonMode.class)); a.setMaterial(ref(in,Material.class));
            int count=in.count(); if (count>8) throw new IOException("Too many M3G texture units");
            for (int i=0;i<count;i++) a.setTexture(i,ref(in,Texture2D.class));
            value=a; break;
        }
        case 4: {
            Background b=new Background(); b.setColor(in.rgba()); b.setImage(ref(in,Image2D.class));
            b.setImageMode(in.u8(),in.u8()); b.setCrop(in.i32(),in.i32(),in.i32(),in.i32());
            b.setDepthClearEnable(in.bool()); b.setColorClearEnable(in.bool()); value=b; break;
        }
        case 5: {
            Camera c=new Camera(); int projection=in.u8();
            if (projection==Camera.GENERIC) { Transform t=new Transform(); t.set(in.floats(16)); c.setGeneric(t); }
            else {
                float fovy=in.f32(),aspect=in.f32(),near=in.f32(),far=in.f32();
                if (projection==Camera.PERSPECTIVE) c.setPerspective(fovy,aspect,near,far);
                else if (projection==Camera.PARALLEL) c.setParallel(fovy,aspect,near,far);
                else throw new IOException("Invalid M3G projection");
            }
            value=c; break;
        }
        case 6: {
            CompositingMode c=new CompositingMode();
            c.setDepthTestEnable(in.bool()); c.setDepthWriteEnable(in.bool());
            c.setColorWriteEnable(in.bool()); c.setAlphaWriteEnable(in.bool());
            c.setBlending(in.u8()); c.setAlphaThreshold(in.u8()/255f);
            c.setDepthOffset(in.f32(),in.f32()); value=c; break;
        }
        case 7: {
            Fog f=new Fog(); f.setColor(in.rgb()); int mode=in.u8(); f.setMode(mode);
            if (mode==Fog.EXPONENTIAL) f.setDensity(in.f32());
            else if (mode==Fog.LINEAR) f.setLinear(in.f32(),in.f32());
            else throw new IOException("Invalid M3G fog");
            value=f; break;
        }
        case 8: {
            PolygonMode p=new PolygonMode(); p.setCulling(in.u8()); p.setShading(in.u8()); p.setWinding(in.u8());
            p.setTwoSidedLightingEnable(in.bool()); p.setLocalCameraLightingEnable(in.bool());
            p.setPerspectiveCorrectionEnable(in.bool()); value=p; break;
        }
        case 9: case 22: {
            Group group=type==22?new World():new Group(); int count=in.count();
            for (int i=0;i<count;i++) group.addChild(ref(in,Node.class));
            if (type==22) {
                ((World)group).setActiveCamera(ref(in,Camera.class));
                ((World)group).setBackground(ref(in,Background.class));
            }
            value=group; break;
        }
        case 10: {
            int format=in.u8(); boolean mutable=in.bool(); int width=in.count(),height=in.count();
            if (width<1||height<1||(long)width*height>4_000_000) throw new IOException("Excessive M3G image size");
            if (mutable) value=new Image2D(format,width,height);
            else {
                byte[] palette=in.data(), pixels=in.data();
                value=palette.length==0?new Image2D(format,width,height,pixels)
                        :new Image2D(format,width,height,pixels,palette);
            }
            break;
        }
        case 11: {
            int encoding=in.u8(),size=encoding&127;
            if (size>2) throw new IOException("Invalid M3G index encoding");
            int start=0; int[] indices=null;
            if (encoding>=128) {
                int count=in.count(); in.need(count*(size==0?4:size==1?1:2)); indices=new int[count];
                for(int i=0;i<count;i++) indices[i]=size==0?in.i32():size==1?in.u8():in.u16();
            } else start=size==0?in.i32():size==1?in.u8():in.u16();
            int count=in.count(); in.need(count*4); int[] strips=new int[count];
            long total=0;
            for(int i=0;i<count;i++) {
                strips[i]=in.count(); total+=strips[i];
                if(total>1_000_000) throw new IOException("Too many M3G triangle indices");
            }
            value=indices==null?new TriangleStripArray(start,strips):new TriangleStripArray(indices,strips); break;
        }
        case 12: {
            Light l=new Light(); l.setAttenuation(in.f32(),in.f32(),in.f32()); l.setColor(in.rgb());
            l.setMode(in.u8()); l.setIntensity(in.f32()); l.setSpotAngle(in.f32()); l.setSpotExponent(in.f32());
            value=l; break;
        }
        case 13: {
            Material m=new Material(); m.setColor(Material.AMBIENT,in.rgb()); m.setColor(Material.DIFFUSE,in.rgba());
            m.setColor(Material.EMISSIVE,in.rgb()); m.setColor(Material.SPECULAR,in.rgb());
            m.setShininess(in.f32()); m.setVertexColorTrackingEnable(in.bool()); value=m; break;
        }
        case 14: {
            VertexBuffer vertices=ref(in,VertexBuffer.class); int count=in.count();
            if (count==0||count>65535) throw new IOException("Invalid M3G submesh count");
            IndexBuffer[] indices=new IndexBuffer[count]; Appearance[] apps=new Appearance[count];
            for(int i=0;i<count;i++) { indices[i]=ref(in,IndexBuffer.class); apps[i]=ref(in,Appearance.class); }
            value=new Mesh(vertices,indices,apps); break;
        }
        case 15: {
            VertexBuffer vertices=ref(in,VertexBuffer.class);
            int submeshCount=in.count();
            if (submeshCount==0||submeshCount>65535) throw new IOException("Invalid M3G morph submesh count");
            IndexBuffer[] indices=new IndexBuffer[submeshCount]; Appearance[] apps=new Appearance[submeshCount];
            for(int i=0;i<submeshCount;i++) { indices[i]=ref(in,IndexBuffer.class); apps[i]=ref(in,Appearance.class); }
            int targetCount=in.count();
            if (targetCount>65535) throw new IOException("Too many M3G morph targets");
            VertexBuffer[] targets=new VertexBuffer[targetCount]; float[] weights=new float[targetCount];
            for(int i=0;i<targetCount;i++) { targets[i]=ref(in,VertexBuffer.class); weights[i]=in.f32(); }
            MorphingMesh mesh=new MorphingMesh(vertices,targets,indices,apps);
            mesh.setWeights(weights);
            value=mesh; break;
        }
        case 16: {
            VertexBuffer vertices=ref(in,VertexBuffer.class);
            int submeshCount=in.count();
            if (submeshCount==0||submeshCount>65535) throw new IOException("Invalid M3G skinned submesh count");
            IndexBuffer[] indices=new IndexBuffer[submeshCount];
            Appearance[] apps=new Appearance[submeshCount];
            for(int i=0;i<submeshCount;i++) {
                indices[i]=ref(in,IndexBuffer.class);
                apps[i]=ref(in,Appearance.class);
            }
            Group skeleton=ref(in,Group.class);
            if (skeleton==null) throw new IOException("M3G SkinnedMesh has no skeleton");
            SkinnedMesh mesh=new SkinnedMesh(vertices,indices,apps,skeleton);
            int transformCount=in.count();
            in.need(transformCount*16);
            skinTransforms=new ArrayList<SkinTransformData>(transformCount);
            for(int i=0;i<transformCount;i++) {
                Node bone=ref(in,Node.class);
                int firstVertex=in.i32();
                int numVertices=in.i32();
                int weight=in.i32();
                skinTransforms.add(new SkinTransformData(bone,firstVertex,numVertices,weight));
            }
            value=mesh; break;
        }
        case 17: {
            Texture2D t=new Texture2D(ref(in,Image2D.class)); t.setBlendColor(in.rgb()); t.setBlending(in.u8());
            t.setWrapping(in.u8(),in.u8()); t.setFiltering(in.u8(),in.u8()); value=t; break;
        }
        case 18: {
            Image2D image=ref(in,Image2D.class); Appearance appearance=ref(in,Appearance.class);
            Sprite3D sprite=new Sprite3D(in.bool(),image,appearance);
            sprite.setCrop(in.i32(),in.i32(),in.i32(),in.i32()); value=sprite; break;
        }
        case 19: value=keyframes(in); break;
        case 20: {
            int size=in.u8(),components=in.u8(),encoding=in.u8(),count=in.u16();
            if ((size!=1&&size!=2)||(encoding!=0&&encoding!=1)) throw new IOException("Invalid M3G vertex encoding");
            VertexArray array=new VertexArray(count,components,size);
            int length=count*components; in.need(length*size);
            short[] values=new short[length]; int[] sums=new int[components];
            for(int i=0;i<length;i++) {
                int component=size==1?(byte)in.u8():(short)in.u16();
                if (encoding==1) { sums[i%components]+=component; component=sums[i%components]; }
                values[i]=(short)component;
            }
            if (size==2) array.set(0,count,values);
            else { byte[] bytes=new byte[length]; for(int i=0;i<length;i++) bytes[i]=(byte)values[i]; array.set(0,count,bytes); }
            value=array; break;
        }
        case 21: {
            VertexBuffer b=new VertexBuffer(); b.setDefaultColor(in.rgba()); VertexArray p=ref(in,VertexArray.class);
            float[] bias=in.floats(3); float scale=in.f32(); b.setPositions(p,scale,bias);
            b.setNormals(ref(in,VertexArray.class)); b.setColors(ref(in,VertexArray.class)); int count=in.count();
            if (count>8) throw new IOException("Too many M3G texture coordinate arrays");
            for(int i=0;i<count;i++) { VertexArray t=ref(in,VertexArray.class); bias=in.floats(3); scale=in.f32(); b.setTexCoords(i,t,scale,bias); }
            value=b; break;
        }
        default: throw new IOException("M3G object type not implemented: " + type);
        }
        value.setUserID(base.id);
        if (!base.parameters.isEmpty()) value.setUserObject(base.parameters);
        for(AnimationTrack track:base.tracks) value.addAnimationTrack(track);
        if (transform!=null) transform.apply((Transformable)value);
        if (node!=null) node.apply((Node)value);
        if (skinTransforms!=null) {
            SkinnedMesh mesh=(SkinnedMesh)value;
            for(SkinTransformData binding:skinTransforms)
                mesh.addTransform(binding.bone,binding.weight,binding.firstVertex,
                        binding.numVertices);
        }
        return value;
    }

    private KeyframeSequence keyframes(M3GInput in) throws IOException {
        int interpolation=in.u8(),repeat=in.u8(),encoding=in.u8();
        int duration=in.count(),first=in.count(),last=in.count(),components=in.count(),count=in.count();
        if(components<1||components>64||count<1||count>65536||(long)count*components>1_000_000)
            throw new IOException("Invalid/excessive M3G keyframes");
        KeyframeSequence sequence=new KeyframeSequence(count,components,interpolation);
        sequence.setDuration(duration); sequence.setRepeatMode(repeat); sequence.setValidRange(first,last);
        float[] bias=null,scale=null;
        if(encoding==1||encoding==2) { bias=in.floats(components); scale=in.floats(components); }
        else if(encoding!=0) throw new IOException("Invalid M3G keyframe encoding");
        for(int i=0;i<count;i++) {
            int time=in.count(); float[] values=new float[components];
            for(int j=0;j<components;j++) values[j]=encoding==0?in.f32()
                    :bias[j]+scale[j]*(encoding==1?in.u8()/255f:in.u16()/65535f);
            sequence.setKeyframe(i,time,values);
        }
        return sequence;
    }
    private TransformData transform(M3GInput in) throws IOException {
        boolean hasComponents=in.bool();
        float[] translation=null,scale=null,axis=null;
        float angle=0f;
        if(hasComponents) {
            translation=in.floats(3);
            scale=in.floats(3);
            angle=in.f32();
            axis=in.floats(3);
        }
        Transform general=null;
        if(in.bool()) { general=new Transform(); general.set(in.floats(16)); }
        return new TransformData(hasComponents,translation,scale,angle,axis,general);
    }
    private static final class TransformData {
        final boolean hasComponents;
        final float[] translation,scale,axis;
        final float angle;
        final Transform general;
        TransformData(boolean hasComponents,float[] translation,float[] scale,
                      float angle,float[] axis,Transform general) {
            this.hasComponents=hasComponents;
            this.translation=translation;
            this.scale=scale;
            this.angle=angle;
            this.axis=axis;
            this.general=general;
        }
        void apply(Transformable target) {
            if(hasComponents) {
                target.setTranslation(translation[0],translation[1],translation[2]);
                target.setScale(scale[0],scale[1],scale[2]);
                if(angle!=0f) target.setOrientation(angle,axis[0],axis[1],axis[2]);
            }
            if(general!=null) target.setTransform(general);
        }
    }
    private final class Base {
        final int id;
        final List<AnimationTrack> tracks=new ArrayList<AnimationTrack>();
        final Hashtable<Integer,byte[]> parameters=new Hashtable<Integer,byte[]>();
        Base(M3GInput in) throws IOException {
            id=in.i32(); int count=in.count(); in.need(count*4);
            for(int i=0;i<count;i++) tracks.add(ref(in,AnimationTrack.class));
            count=in.count();
            for(int i=0;i<count;i++) {
                int id=in.i32(); if(parameters.put(id,in.data())!=null) throw new IOException("Duplicate M3G user parameter");
            }
        }
    }
    private final class NodeState {
        final boolean rendering,picking;
        final float alpha;
        final int scope,zTarget,yTarget;
        final Node zRef,yRef;
        NodeState(M3GInput in) throws IOException {
            rendering=in.bool(); picking=in.bool(); alpha=in.u8()/255f; scope=in.i32();
            if(in.bool()) { zTarget=in.u8(); yTarget=in.u8(); zRef=ref(in,Node.class); yRef=ref(in,Node.class); }
            else { zTarget=Node.NONE; yTarget=Node.NONE; zRef=null; yRef=null; }
        }
        void apply(Node n) {
            n.setRenderingEnable(rendering); n.setPickingEnable(picking); n.setAlphaFactor(alpha); n.setScope(scope);
            n.setAlignment(zRef,zTarget,yRef,yTarget);
        }
    }
}
