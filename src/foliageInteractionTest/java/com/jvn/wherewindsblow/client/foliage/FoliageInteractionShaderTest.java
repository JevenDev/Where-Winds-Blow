package com.jvn.wherewindsblow.client.foliage;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL33C;

public final class FoliageInteractionShaderTest {
    private static int checks;

    public static void main(String[] args) throws ReflectiveOperationException {
        GLFWErrorCallback errors = GLFWErrorCallback.createPrint(System.err);
        errors.set();
        if (!GLFW.glfwInit()) {
            errors.free();
            throw new AssertionError("Could not initialize GLFW for foliage shader checks");
        }
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        long window = GLFW.glfwCreateWindow(1, 1, "Foliage interaction checks", 0L, 0L);
        if (window == 0L) {
            GLFW.glfwTerminate();
            errors.free();
            throw new AssertionError("Could not create a hidden OpenGL context");
        }
        try {
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
            int program = createProgram();
            int vertices = GL33C.glGenVertexArrays();
            int output = GL33C.glGenBuffers();
            try {
                GL33C.glBindVertexArray(vertices);
                GL33C.glBindBuffer(GL33C.GL_TRANSFORM_FEEDBACK_BUFFER, output);
                GL33C.glBufferData(GL33C.GL_TRANSFORM_FEEDBACK_BUFFER, 6L * Float.BYTES, GL33C.GL_STREAM_READ);
                GL33C.glBindBufferBase(GL33C.GL_TRANSFORM_FEEDBACK_BUFFER, 0, output);
                GL33C.glEnable(GL33C.GL_RASTERIZER_DISCARD);
                GL33C.glUseProgram(program);
                GL33C.glUniform1i(GL33C.glGetUniformLocation(program, "u_WwbInteractorCount"), 1);

                for (float groundY : new float[]{-128.5F, -64.0F, 0.0F, 64.0F, 127.25F}) {
                    entity(program, groundY + 1.0F, groundY + 2.8F, 1.2F, 0.0F);
                    noContact(sample(program, groundY + 1.0F), "feet level with the foliage top");
                    noContact(sample(program, groundY + 0.8F), "foliage below the feet");
                    entity(program, groundY + 1.25F, groundY + 3.05F, 1.2F, 0.0F);
                    noContact(sample(program, groundY + 1.0F), "a gap above foliage");
                    entity(program, groundY + 1.0F, groundY + 4.0F, 2.6F, -1.55F);
                    noContact(sample(program, groundY + 1.0F), "a large moving entity above foliage");
                    entity(program, groundY, groundY + 1.8F, 1.2F, 0.0F);
                    float[] fullContact = sample(program, groundY + 1.0F);
                    contact(fullContact, "direct grass contact");
                    contact(sample(program, groundY + 2.0F), "tall grass tips above the body");
                    entity(program, groundY + 0.95F, groundY + 2.75F, 1.2F, 0.0F);
                    float[] partialContact = sample(program, groundY + 1.0F);
                    contact(partialContact, "partial contact near the foliage top");
                    check(partialContact[0] < fullContact[0], "contact eases as feet leave the grass");
                    entity(program, groundY + 0.999F, groundY + 2.799F, 1.2F, 0.0F);
                    check(sample(program, groundY + 1.0F)[0] < fullContact[0] * 0.001F,
                            "tiny height differences cannot sustain visible contact");
                }

                radialForce(program);
                contact(sample(program, 0.25F), "radial forces retain their lower vertical falloff");
                noContact(sample(program, -2.0F), "radial forces still have a limited range");
                check(GL33C.glGetError() == GL33C.GL_NO_ERROR, "shader checks complete without OpenGL errors");
                System.out.println("Passed " + checks + " foliage shader checks on " + GL33C.glGetString(GL33C.GL_VERSION) + ".");
            } finally {
                GL33C.glDeleteBuffers(output);
                GL33C.glDeleteVertexArrays(vertices);
                GL33C.glDeleteProgram(program);
            }
        } finally {
            GLFW.glfwDestroyWindow(window);
            GLFW.glfwTerminate();
            errors.free();
        }
    }

    private static int createProgram() {
        String source = "#version 150\n#define WWB_HAS_FOLIAGE_MATERIAL true\n"
                + FoliageShaderSource.UNIFORMS + FoliageShaderSource.FUNCTIONS + """
                uniform float sampleY;
                out vec3 interaction;
                out vec3 windPosition;
                void main() {
                    vec3 base = vec3(0.0, sampleY, 0.0);
                    vec2 anchor = vec2(0.5, 0.0);
                    interaction = wwb_foliage_interaction_offset(base, anchor, 1.0);
                    windPosition = wwb_apply_foliage_interaction(
                            base + vec3(0.1, 0.0, 0.05), base, anchor, 132.0 / 255.0
                    );
                    gl_Position = vec4(base, 1.0);
                }
                """;
        int shader = GL33C.glCreateShader(GL33C.GL_VERTEX_SHADER);
        int program = GL33C.glCreateProgram();
        try {
            GL33C.glShaderSource(shader, source);
            GL33C.glCompileShader(shader);
            check(GL33C.glGetShaderi(shader, GL33C.GL_COMPILE_STATUS) != 0, GL33C.glGetShaderInfoLog(shader));
            GL33C.glAttachShader(program, shader);
            GL33C.glTransformFeedbackVaryings(program, new String[]{"interaction", "windPosition"}, GL33C.GL_INTERLEAVED_ATTRIBS);
            GL33C.glLinkProgram(program);
            check(GL33C.glGetProgrami(program, GL33C.GL_LINK_STATUS) != 0, GL33C.glGetProgramInfoLog(program));
            return program;
        } catch (AssertionError failure) {
            GL33C.glDeleteProgram(program);
            throw failure;
        } finally {
            GL33C.glDeleteShader(shader);
        }
    }

    private static void entity(int program, float minY, float maxY, float radius, float trailX) {
        GL33C.glUniform4f(GL33C.glGetUniformLocation(program, "u_WwbInteractor0"), 0.0F, minY, 0.0F, radius);
        GL33C.glUniform4f(GL33C.glGetUniformLocation(program, "u_WwbInteractorMotion0"), maxY, trailX, 0.0F, 1.0F);
    }

    private static void radialForce(int program) throws ReflectiveOperationException {
        Class<?> forceType = Class.forName(ResponsiveFoliagePhysics.class.getName() + "$TransientForce");
        Constructor<?> constructor = forceType.getDeclaredConstructor(
                double.class, double.class, double.class, float.class, float.class, long.class, long.class
        );
        constructor.setAccessible(true);
        Object force = constructor.newInstance(0.0D, 1.0D, 0.0D, 1.2F, 1.0F, 1000L, 900L);
        Class<?> sampleType = Class.forName(ResponsiveFoliagePhysics.class.getName() + "$ShaderInteraction");
        Method fromForce = sampleType.getDeclaredMethod("fromForce", forceType, long.class);
        fromForce.setAccessible(true);
        Object sample = fromForce.invoke(null, force, 1000L);
        Method fill = ResponsiveFoliagePhysics.class.getDeclaredMethod(
                "fillShaderInteractor", sampleType, double[].class, double[].class, int.class
        );
        fill.setAccessible(true);
        double[] interactor = new double[4];
        double[] motion = new double[4];
        fill.invoke(null, sample, interactor, motion, 0);
        GL33C.glUniform4f(GL33C.glGetUniformLocation(program, "u_WwbInteractor0"),
                (float) interactor[0], (float) interactor[1], (float) interactor[2], (float) interactor[3]);
        GL33C.glUniform4f(GL33C.glGetUniformLocation(program, "u_WwbInteractorMotion0"),
                (float) motion[0], (float) motion[1], (float) motion[2], (float) motion[3]);
    }

    private static float[] sample(int program, float y) {
        GL33C.glUniform1f(GL33C.glGetUniformLocation(program, "sampleY"), y);
        GL33C.glBeginTransformFeedback(GL33C.GL_POINTS);
        GL33C.glDrawArrays(GL33C.GL_POINTS, 0, 1);
        GL33C.glEndTransformFeedback();
        float[] result = new float[6];
        GL33C.glGetBufferSubData(GL33C.GL_TRANSFORM_FEEDBACK_BUFFER, 0L, result);
        return result;
    }

    private static void noContact(float[] result, String description) {
        check(result[0] == 0.0F && result[1] == 0.0F && result[2] == 0.0F, description);
        check(Math.abs(result[3] - 0.1F) < 0.00001F && Math.abs(result[5] - 0.05F) < 0.00001F,
                description + " preserves normal wind movement");
    }

    private static void contact(float[] result, String description) {
        check(result[0] > 0.0F && result[2] > 0.0F, description);
    }

    private static void check(boolean condition, String description) {
        if (!condition) {
            throw new AssertionError(description);
        }
        checks++;
    }
}
