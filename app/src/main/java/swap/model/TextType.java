package swap.model;

// TODO: might be a better way to do this with css files

public enum TextType {
    PLAIN {
        public String toString() {
            return styling;
        }
    },
    WHITE {
        public String toString() {
            return styling + " " + border + " " + white;
        }
    },
    RED {
        public String toString() {
            return styling + " " + border + " " + red;
        }
    },
    ERROR {
        public String toString() {
            return styling + " " + border + " " + error;
        }
    },
    SUCCESS {
        public String toString() {
            return styling + " " + border + " " + success;
        }
    };

    private static final String styling = "-fx-background-color: #0b0c0f; -fx-text-fill: white;";
    private static final String border = "-fx-border-radius: 3px;";
    private static final String white = "-fx-border-color: white;";
    private static final String red = "-fx-border-color: #c12727;";
    private static final String error = "-fx-border-color: #d42b2b;";
    private static final String success = "-fx-border-color: #27be4d;";
}
