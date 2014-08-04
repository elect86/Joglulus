/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package joglulus.example;

import com.jogamp.newt.event.KeyEvent;

/**
 *
 * @author gbarbieri
 */
public class KeyListener implements com.jogamp.newt.event.KeyListener {

    private GlViewer glViewer;

    public KeyListener(GlViewer glViewer) {

        this.glViewer = glViewer;
    }

    @Override
    public void keyPressed(KeyEvent ke) {

        if (ke.getKeyCode() == KeyEvent.VK_F5) {

            glViewer.toggleFullscreen();
        }
        glViewer.getGlWindow().display();
    }

    @Override
    public void keyReleased(KeyEvent ke) {

    }

}
