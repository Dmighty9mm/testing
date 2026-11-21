package net.lax1dude.eaglercraft.v2_6.client.apollo;

/**
 * Thrown by a handler that recognizes a message but refuses it by this
 * client's policy (for example a server request to enable X-ray). The
 * router logs it as IGNORED_POLICY; it is not an error.
 */
public class PolicyException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public PolicyException(String message) {
                super(message);
        }
}
