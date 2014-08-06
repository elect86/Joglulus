/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package joglus.example1;

import com.jogamp.newt.awt.NewtCanvasAWT;
import com.jogamp.newt.opengl.GLWindow;
import com.jogamp.opengl.util.Animator;
import com.jogamp.opengl.util.FPSAnimator;
import com.jogamp.opengl.util.GLBuffers;
import com.jogamp.opengl.util.GLReadBufferUtil;
import com.jogamp.opengl.util.texture.TextureIO;
import com.oculusvr.capi.FovPort;
import com.oculusvr.capi.HmdDesc;
import com.oculusvr.capi.OvrLibrary;
import static com.oculusvr.capi.OvrLibrary.ovrHmdType.ovrHmd_DK1;
import static com.oculusvr.capi.OvrLibrary.ovrTrackingCaps.ovrTrackingCap_Orientation;
import com.oculusvr.capi.OvrRecti;
import com.oculusvr.capi.OvrSizei;
import com.oculusvr.capi.OvrVector2i;
import java.awt.Frame;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.media.opengl.GL3;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLCapabilities;
import javax.media.opengl.GLEventListener;
import javax.media.opengl.GLException;
import javax.media.opengl.GLProfile;
import jglm.Jglm;
import jglm.Mat4;
import joglus.example1.glsl.Distortion;
import joglus.example1.glsl.Program;
import joglus.example1.texture.Texture;
import joglus.example1.texture.TextureFormat;

/**
 *
 * @author gbarbieri
 */
public class GlViewer implements GLEventListener {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {

        final GlViewer glViewer = new GlViewer();
        glViewer.initGL();
        glViewer.setupOculus();

        Frame frame = new Frame("Joglus");

        frame.add(glViewer.newtCanvasAWT);

        frame.setSize(glViewer.glWindow.getWidth(), glViewer.glWindow.getHeight());

        final FPSAnimator fPSAnimator = new FPSAnimator(glViewer.glWindow, 3);

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent windowEvent) {
                fPSAnimator.stop();
                glViewer.glWindow.destroy();
                System.exit(0);
            }
        });

        fPSAnimator.start();
        frame.setVisible(true);
    }

    private GLWindow glWindow;
    private NewtCanvasAWT newtCanvasAWT;
    private boolean fullscreen;
    private KeyListener keyListener;
    private Animator animator;
    private int[] vbo;
    private int[] vao;
    private int[] vboDistortion;
    private int[] vaoDistortion;
    private float[] verticesData;
    private Program program;
    private Distortion distortion;

    private HmdDesc hmdDesc;
    private OvrRecti[] eyeRenderViewport;
    private int frameCount;
    private FrameBuffer frameBuffer;

    public GlViewer() {
    }

    private void initGL() {
        System.out.println("initGL()");
        GLProfile gLProfile = GLProfile.getDefault();

        GLCapabilities gLCapabilities = new GLCapabilities(gLProfile);

        glWindow = GLWindow.create(gLCapabilities);
        /*
         *  We combine NEWT GLWindow inside existing AWT application (the main JFrame)
         *  by encapsulating the glWindow inside a NewtCanvasAWT canvas.
         */
        newtCanvasAWT = new NewtCanvasAWT(glWindow);

        keyListener = new KeyListener(this);
        glWindow.addKeyListener(keyListener);

        glWindow.addGLEventListener(this);
        fullscreen = false;
        glWindow.setFullscreen(fullscreen);

//        animator = new Animator(glWindow);
//        animator.start();
        glWindow.setSize(1280, 800);
//        glWindow.setSize(2300, 1600);
//        glWindow.setVisible(true);
        System.out.println("/initGL()");
    }

    private void setupOculus() {
        System.out.println("setupOculus()");
        // Initializes LibOVR, and the Rift
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
        frameCount = -1;

        System.out.println("/setupOculus()");
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

        initVBOs(gl3);

        initVAOs(gl3);

        program = new Program(gl3, "/joglus/example1/glsl/shaders/", "Colored_VS.glsl", "Colored_FS.glsl");
        distortion = new Distortion(gl3, "/joglus/example1/glsl/shaders/", "Distortion_VS.glsl", "Distortion_FS.glsl");

        program.bind(gl3);
        {
            Mat4 modelView = new Mat4(1f);

            gl3.glUniformMatrix4fv(program.getModelViewUL(), 1, false, modelView.toFloatArray(), 0);
        }
        program.unbind(gl3);

        distortion.bind(gl3);
        {
            Mat4 modelToClipMatrix = Jglm.orthographic2D(0, 1, 0, 1);

            gl3.glUniformMatrix4fv(distortion.getModelToClipMatrixUL(), 1, false, modelToClipMatrix.toFloatArray(), 0);
        }
        distortion.unbind(gl3);

        initOculus(gl3);
    }

    private void initVBOs(GL3 gl3) {

        verticesData = new float[]{
            -1f, -1f, -1f,
            0f, 1f, -1f,
            1f, 0f, -1};

        vbo = new int[1];
        gl3.glGenBuffers(1, vbo, 0);

        gl3.glBindBuffer(GL3.GL_ARRAY_BUFFER, vbo[0]);
        {
            gl3.glBufferData(GL3.GL_ARRAY_BUFFER, verticesData.length * 4, GLBuffers.newDirectFloatBuffer(verticesData), GL3.GL_STATIC_DRAW);
        }
        gl3.glBindBuffer(GL3.GL_ARRAY_BUFFER, 0);

        verticesData = new float[]{
            0f, 0f,
            1f, 0f,
            1f, 1f,
            0f, 1f};

        vboDistortion = new int[1];
        gl3.glGenBuffers(1, vboDistortion, 0);

        gl3.glBindBuffer(GL3.GL_ARRAY_BUFFER, vboDistortion[0]);
        {
            gl3.glBufferData(GL3.GL_ARRAY_BUFFER, verticesData.length * 4, GLBuffers.newDirectFloatBuffer(verticesData), GL3.GL_STATIC_DRAW);
        }
        gl3.glBindBuffer(GL3.GL_ARRAY_BUFFER, 0);
    }

    private void initVAOs(GL3 gl3) {

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

        vaoDistortion = new int[1];
        gl3.glGenVertexArrays(1, vaoDistortion, 0);

        gl3.glBindBuffer(GL3.GL_ARRAY_BUFFER, vboDistortion[0]);
        {
            gl3.glBindVertexArray(vaoDistortion[0]);
            {
                gl3.glEnableVertexAttribArray(0);
                gl3.glVertexAttribPointer(0, 2, GL3.GL_FLOAT, false, 0, 0);
            }
            gl3.glBindVertexArray(0);
        }
        gl3.glBindBuffer(GL3.GL_ARRAY_BUFFER, 0);
    }

    private void initOculus(GL3 gl3) {
        //Configure Stereo settings.
        OvrSizei recommendedTex0Size = hmdDesc.getFovTextureSize(OvrLibrary.ovrEyeType.ovrEye_Left, hmdDesc.DefaultEyeFov[0], 1f);
        OvrSizei recommendedTex1Size = hmdDesc.getFovTextureSize(OvrLibrary.ovrEyeType.ovrEye_Right, hmdDesc.DefaultEyeFov[1], 1f);
        int x = recommendedTex0Size.w + recommendedTex1Size.w;
        int y = Math.max(recommendedTex0Size.h, recommendedTex1Size.h);
        OvrSizei renderTargetSize = new OvrSizei(x, y);

//        Texture pRenderTargetTexture = Texture.create(gl3, TextureFormat.RGBA, renderTargetSize, null);
        frameBuffer = new FrameBuffer(gl3, renderTargetSize);
        // Initialize eye rendering information.
        FovPort[] eyeFov = new FovPort[]{hmdDesc.DefaultEyeFov[0], hmdDesc.DefaultEyeFov[1]};

        eyeRenderViewport = new OvrRecti[]{new OvrRecti(), new OvrRecti()};
        eyeRenderViewport[0].Pos = new OvrVector2i(0, 0);
        eyeRenderViewport[0].Size = new OvrSizei(renderTargetSize.w / 2, renderTargetSize.h);
        eyeRenderViewport[1].Pos = new OvrVector2i((renderTargetSize.w + 1) / 2, 0);
        eyeRenderViewport[1].Size = eyeRenderViewport[0].Size;
    }

    @Override
    public void dispose(GLAutoDrawable glad) {

        System.out.println("dispose");

        hmdDesc.destroy();
//        HmdDesc.shutdown();
//        System.exit(0);
    }

    @Override
    public void display(GLAutoDrawable glad) {

        System.out.println("display");
        GL3 gl3 = glad.getGL().getGL3();

        hmdDesc.beginFrameTiming(++frameCount);
        {
            gl3.glBindFramebuffer(GL3.GL_FRAMEBUFFER, frameBuffer.getId()[0]);
            gl3.glDrawBuffer(GL3.GL_COLOR_ATTACHMENT0);
            {
                gl3.glClearColor(0, 0, 0, 1);
                gl3.glClear(GL3.GL_COLOR_BUFFER_BIT | GL3.GL_DEPTH_BUFFER_BIT);

                for (int eyeIndex = 0; eyeIndex < OvrLibrary.ovrEyeType.ovrEye_Count; eyeIndex++) {

                    OvrRecti vp = eyeRenderViewport[eyeIndex];

                    gl3.glViewport(vp.Pos.x, vp.Pos.y, vp.Size.w, vp.Size.h);
                    gl3.glEnable(GL3.GL_DEPTH_TEST);
                    {
                        render(gl3);
                    }
                    gl3.glDisable(GL3.GL_DEPTH_TEST);
                }
            }
//            saveImage(gl3);

            gl3.glBindFramebuffer(GL3.GL_FRAMEBUFFER, 0);
            gl3.glDrawBuffer(GL3.GL_BACK);

            gl3.glViewport(0, 0, glWindow.getWidth(), glWindow.getHeight());

//            gl3.glClearColor(0, 1, 0, 1);
//            gl3.glClear(GL3.GL_COLOR_BUFFER_BIT | GL3.GL_DEPTH_BUFFER_BIT);
            renderFullScreenQuad(gl3);
        }
        hmdDesc.endFrameTiming();
//                gl3.glClearColor(0, 0, 0, 1);
//                gl3.glClear(GL3.GL_COLOR_BUFFER_BIT | GL3.GL_DEPTH_BUFFER_BIT);
//                
//        render(gl3);
        checkError(gl3);
    }

    private void saveImage(GL3 gl3) {

        GLReadBufferUtil bufferUtil = new GLReadBufferUtil(true, true);

        bufferUtil.readPixels(gl3, 0, 0, frameBuffer.getSize().w, frameBuffer.getSize().h, false);

        com.jogamp.opengl.util.texture.Texture texture = bufferUtil.getTexture();

        try {
            TextureIO.write(texture, new File("D:\\Downloads\\texture.png"));
        } catch (IOException | GLException ex) {
            Logger.getLogger(GlViewer.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void render(GL3 gl3) {

        program.bind(gl3);
        {
            gl3.glBindBuffer(GL3.GL_ARRAY_BUFFER, vbo[0]);
            {
                gl3.glBindVertexArray(vao[0]);
                {
                    gl3.glDrawArrays(GL3.GL_TRIANGLES, 0, 3);
                }
                gl3.glBindVertexArray(0);
            }
            gl3.glBindBuffer(GL3.GL_ARRAY_BUFFER, 0);
        }
        program.unbind(gl3);
    }

    private void renderFullScreenQuad(GL3 gl3) {

        distortion.bind(gl3);
        {
            gl3.glBindBuffer(GL3.GL_ARRAY_BUFFER, vboDistortion[0]);
            {
                gl3.glBindVertexArray(vaoDistortion[0]);
                {
                    gl3.glActiveTexture(GL3.GL_TEXTURE0);
                    gl3.glBindTexture(GL3.GL_TEXTURE_RECTANGLE, frameBuffer.getTextureId()[0]);
                    gl3.glUniform1i(distortion.getTexture0UL(), 0);

                    gl3.glDrawArrays(GL3.GL_QUADS, 0, 4);
                }
                gl3.glBindVertexArray(0);
            }
            gl3.glBindBuffer(GL3.GL_ARRAY_BUFFER, 0);
        }
        distortion.unbind(gl3);
    }

    @Override
    public void reshape(GLAutoDrawable glad, int i, int i1, int i2, int i3) {

        System.out.println("reshape (" + i + ", " + i1 + ") (" + i2 + ", " + i3 + ") glWindow (" + glWindow.getWidth() + ", " + glWindow.getHeight() + ")");

        GL3 gl3 = glad.getGL().getGL3();
//        gl3.glViewport(i, i1, i2, i3);

        program.bind(gl3);
        {
            float size = 1f;

            Mat4 projection = Jglm.orthographic(-size, size, -size, size, -size * 2, size * 2);

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
