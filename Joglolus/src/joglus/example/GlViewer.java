/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package joglus.example;

import com.jogamp.newt.awt.NewtCanvasAWT;
import com.jogamp.newt.opengl.GLWindow;
import com.jogamp.opengl.util.Animator;
import com.jogamp.opengl.util.GLBuffers;
import com.oculusvr.capi.EyeRenderDesc;
import com.oculusvr.capi.FovPort;
import com.oculusvr.capi.HmdDesc;
import static com.oculusvr.capi.OvrLibrary.ovrDistortionCaps.ovrDistortionCap_Chromatic;
import static com.oculusvr.capi.OvrLibrary.ovrDistortionCaps.ovrDistortionCap_TimeWarp;
import static com.oculusvr.capi.OvrLibrary.ovrDistortionCaps.ovrDistortionCap_Vignette;
import static com.oculusvr.capi.OvrLibrary.ovrHmdType.ovrHmd_DK1;
import static com.oculusvr.capi.OvrLibrary.ovrTrackingCaps.ovrTrackingCap_Orientation;
import com.oculusvr.capi.OvrVector2i;
import com.oculusvr.capi.Posef;
import com.oculusvr.capi.RenderAPIConfig;
import com.oculusvr.capi.Texture;
import com.oculusvr.capi.TextureHeader;
import javax.media.opengl.GL3;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLCapabilities;
import javax.media.opengl.GLEventListener;
import javax.media.opengl.GLProfile;
import jglm.Jglm;
import jglm.Mat4;
import org.saintandreas.math.Matrix4f;
import joglus.example.glsl.Program;
import jglm.Vec2i;
import org.saintandreas.gl.MatrixStack;

/**
 *
 * @author gbarbieri
 */
public class GlViewer implements GLEventListener {

    public static void main(String[] args) {

        new GlViewer();
    }

    private GLWindow glWindow;
    private NewtCanvasAWT newtCanvasAWT;
    private boolean fullscreen;
    private KeyListener keyListener;
    private Animator animator;
    private int[] vbo;
    private int[] vao;
    private float[] verticesData;
    private Program program;

    private HmdDesc hmdDesc;
    private FovPort[] fovPorts;
    private Texture[] eyeTextures;
    private FrameBuffer[] frameBuffers;
    private Matrix4f[] projections;
    private EyeRenderDesc[] eyeRenderDescs;
    private Posef[] poses;
    private int frameCount;

    public GlViewer() {

        setup();

        setupOculus();
        
        animator = new Animator(glWindow);
        animator.start();
        
        glWindow.setVisible(true);
    }

    private void setup() {
        GLProfile gLProfile = GLProfile.getDefault();

        GLCapabilities gLCapabilities = new GLCapabilities(gLProfile);

        glWindow = GLWindow.create(gLCapabilities);
        /*
         *  We combine NEWT GLWindow inside existing AWT application (the main JFrame)
         *  by encapsulating the glWindow inside a NewtCanvasAWT canvas.
         */
        newtCanvasAWT = new NewtCanvasAWT(glWindow);

        glWindow.setSize(1280, 800);
        glWindow.addGLEventListener(this);
        keyListener = new KeyListener(this);
        glWindow.addKeyListener(keyListener);

        fullscreen = false;
        glWindow.setFullscreen(fullscreen);
    }

    private void setupOculus() {
        System.out.println("1");
        HmdDesc.initialize();

        try {
            Thread.sleep(400);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }

        hmdDesc = openFirstHmd();

        if (null == hmdDesc) {
            throw new IllegalStateException("Unable to initialize HMD");
        }
        if (hmdDesc.configureTracking(ovrTrackingCap_Orientation, 0) == 0) {
            throw new IllegalStateException("Unable to start the sensor");
        }
        fovPorts = (FovPort[]) new FovPort().toArray(2);
        eyeTextures = (Texture[]) new Texture().toArray(2);
        poses = (Posef[]) new Posef().toArray(2);
        frameBuffers = new FrameBuffer[2];
        projections = new Matrix4f[2];
        frameCount = -1;

        for (int eye = 0; eye < 2; ++eye) {

            fovPorts[eye] = hmdDesc.DefaultEyeFov[eye];
            projections[eye] = RiftUtils.toMatrix4f(HmdDesc.getPerspectiveProjection(fovPorts[eye], 0.1f, 1000000f, true));

            Texture texture = eyeTextures[eye];
            TextureHeader header = texture.Header;
            header.TextureSize = hmdDesc.getFovTextureSize(eye, fovPorts[eye], 1.0f);
            header.RenderViewport.Size = header.TextureSize;
            header.RenderViewport.Pos = new OvrVector2i(0, 0);
        }
        System.out.println("/1");
    }

    private static HmdDesc openFirstHmd() {
        HmdDesc hmdDesc = HmdDesc.create(0);
        if (hmdDesc == null) {
            hmdDesc = HmdDesc.createDebug(ovrHmd_DK1);
        }
        return hmdDesc;
    }

    @Override
    public void init(GLAutoDrawable glad) {
        System.out.println("init");

        GL3 gl3 = glad.getGL().getGL3();

        initVBO(gl3);

        initVAO(gl3);

        program = new Program(gl3, "/joglus/example/glsl/shaders/", "Colored_VS.glsl", "Colored_FS.glsl");

        program.bind(gl3);
        {
            Mat4 modelView = new Mat4(1f);

            gl3.glUniformMatrix4fv(program.getModelViewUL(), 1, false, modelView.toFloatArray(), 0);
        }
        program.unbind(gl3);

        initOculus(gl3);
    }

    private void initOculus(GL3 gl3) {
        System.out.println("2");
        for (int eye = 0; eye < 2; ++eye) {
            TextureHeader eth = eyeTextures[eye].Header;
            frameBuffers[eye] = new FrameBuffer(gl3, new Vec2i(eth.TextureSize.w, eth.TextureSize.h));
            eyeTextures[eye].TextureId = frameBuffers[eye].getTextureId()[0];
        }

        RenderAPIConfig rc = new RenderAPIConfig();
        rc.Header.RTSize = hmdDesc.Resolution;
        rc.Header.Multisample = 1;

        int distortionCaps = ovrDistortionCap_Chromatic | ovrDistortionCap_TimeWarp | ovrDistortionCap_Vignette;

        eyeRenderDescs = hmdDesc.configureRendering(rc, distortionCaps, fovPorts);
        System.out.println("/2");
    }

    private void initVBO(GL3 gl3) {

        verticesData = new float[]{
            0f, -1f, -1f,
            0f, 1f, -1f,
            1f, 0f, -1};

        vbo = new int[1];
        gl3.glGenBuffers(1, vbo, 0);

        gl3.glBindBuffer(GL3.GL_ARRAY_BUFFER, vbo[0]);
        {
            gl3.glBufferData(GL3.GL_ARRAY_BUFFER, verticesData.length * 4, GLBuffers.newDirectFloatBuffer(verticesData), GL3.GL_STATIC_DRAW);
        }
        gl3.glBindBuffer(GL3.GL_ARRAY_BUFFER, 0);
    }

    private void initVAO(GL3 gl3) {

        vao = new int[1];
        gl3.glGenVertexArrays(1, vao, 0);

        gl3.glBindBuffer(GL3.GL_ARRAY_BUFFER, vbo[0]);
        {
            gl3.glBindVertexArray(vao[0]);
            {
                gl3.glEnableVertexAttribArray(0);
                gl3.glVertexAttribPointer(0, 3, GL3.GL_FLOAT, false, 0, 0);
            }
            gl3.glBindVertexArray(0);
        }
        gl3.glBindBuffer(GL3.GL_ARRAY_BUFFER, 0);
    }

    @Override
    public void dispose(GLAutoDrawable glad) {

        System.out.println("dispose");

        hmdDesc.destroy();
        HmdDesc.shutdown();
        System.exit(0);
    }

    @Override
    public void display(GLAutoDrawable glad) {

        System.out.println("display");
        GL3 gl3 = glad.getGL().getGL3();

        hmdDesc.beginFrame(++frameCount);
        for (int i = 0; i < 2; ++i) {
            int eye = hmdDesc.EyeRenderOrder[i];
//            MatrixStack.PROJECTION.set(projections[eye]);
//            // This doesn't work as it breaks the contiguous nature of the array
//            Posef p = hmdDesc.getEyePose(eye);
//            // FIXME there has to be a better way to do this
//            poses[eye].Orientation = p.Orientation;
//            poses[eye].Position = p.Position;
//
//            MatrixStack mv = MatrixStack.MODELVIEW;
//            mv.push();
            {
//                mv.preTranslate(RiftUtils.toVector3f(poses[eye].Position).mult(-1));
//                mv.preRotate(RiftUtils.toQuaternion(poses[eye].Orientation).inverse());
//                mv.preTranslate(RiftUtils.toVector3f(eyeRenderDescs[eye].ViewAdjust));
                frameBuffers[eye].activate(gl3);
                {
                    render(gl3);
                }
                frameBuffers[eye].deactivate(gl3);
            }
//            mv.pop();
        }
        hmdDesc.endFrame(poses, eyeTextures);

        checkError(gl3);
    }

    private void render(GL3 gl3) {

        gl3.glClearColor(0, 0, 0, 1);
        gl3.glClear(GL3.GL_COLOR_BUFFER_BIT | GL3.GL_DEPTH_BUFFER_BIT);

        gl3.glEnable(GL3.GL_DEPTH_TEST);
        {

            gl3.glBindBuffer(GL3.GL_ARRAY_BUFFER, vbo[0]);
            {
                gl3.glBindVertexArray(vao[0]);
                {
                    gl3.glDrawArrays(GL3.GL_TRIANGLES, 0, verticesData.length / 3);
                }
                gl3.glBindVertexArray(0);
            }
            gl3.glBindBuffer(GL3.GL_ARRAY_BUFFER, 0);
        }
        gl3.glDisable(GL3.GL_DEPTH_TEST);
    }

    @Override
    public void reshape(GLAutoDrawable glad, int i, int i1, int i2, int i3) {

        System.out.println("reshape (" + i + ", " + i1 + ") (" + i2 + ", " + i3 + ")");

        GL3 gl3 = glad.getGL().getGL3();
        gl3.glViewport(i, i1, i2, i3);

        program.bind(gl3);
        {
            float size = 5f;

            Mat4 projection = Jglm.orthographic(-size, size, -size, size, -size, size);

            gl3.glUniformMatrix4fv(program.getProjectionUL(), 1, false, projection.toFloatArray(), 0);
        }
        program.unbind(gl3);
    }

    public void toggleFullscreen() {

        fullscreen = !fullscreen;

        glWindow.setFullscreen(fullscreen);
    }

    public GLWindow getGlWindow() {
        return glWindow;
    }

    public NewtCanvasAWT getNewtCanvasAWT() {
        return newtCanvasAWT;
    }

    public Animator getAnimator() {
        return animator;
    }

    private void checkError(GL3 gl3) {

        int error = gl3.glGetError();

        if (error != 0) {
            System.out.println("Error " + error + " !");
        }
    }
}
