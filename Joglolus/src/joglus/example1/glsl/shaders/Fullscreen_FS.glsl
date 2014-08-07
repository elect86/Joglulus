#version 330

out vec4 outputColor;

uniform sampler2D texture0;

in vec2 texCoord;

void main() {

    outputColor = texture(texture0, texCoord);
}