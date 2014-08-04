/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package joglus.example.glsl;

import javax.media.opengl.GL3;

/**
 *
 * @author gbarbieri
 */
public class Program extends glsl.GLSLProgramObject {

    private int projectionUL;
    private int modelViewUL;

    public Program(GL3 gl3, String shadersFilepath, String vertexShader, String fragmentShader) {
        super(gl3, shadersFilepath, vertexShader, fragmentShader);

        projectionUL = gl3.glGetUniformLocation(getProgramId(), "projection");
        modelViewUL = gl3.glGetUniformLocation(getProgramId(), "modelView");
    } 

    public int getProjectionUL() {
        return projectionUL;
    }

    public int getModelViewUL() {
        return modelViewUL;
    }
}
