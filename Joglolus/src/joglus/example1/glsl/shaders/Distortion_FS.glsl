#version 330

out vec4 outputColor;

uniform sampler2DRect texture0;

void main() {

    outputColor = texture(texture0, gl_FragCoord.xy);
}