/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package joglus.example1.glsl;

import javax.media.opengl.GL3;

/**
 *
 * @author gbarbieri
 */
public class Distortion extends glsl.GLSLProgramObject {

    private int modelToClipMatrixUL;
    private int texture0UL;

    public Distortion(GL3 gl3, String shadersFilepath, String vertexShader, String fragmentShader) {

        super(gl3, shadersFilepath, vertexShader, fragmentShader);

        modelToClipMatrixUL = gl3.glGetUniformLocation(getProgramId(), "modelToClipMatrix");
        
        texture0UL = gl3.glGetUniformLocation(getProgramId(), "texture0");
    }

    public int getModelToClipMatrixUL() {
        return modelToClipMatrixUL;
    }

    public int getTexture0UL() {
        return texture0UL;
    }
}
