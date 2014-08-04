#version 330

uniform mat4 projection;
uniform mat4 modelView;

layout(location = 0) in vec3 position;

void main() {
    gl_Position = projection * modelView * vec4(position, 1);
}
  