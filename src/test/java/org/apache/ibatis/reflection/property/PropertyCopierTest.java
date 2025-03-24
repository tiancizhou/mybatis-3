package org.apache.ibatis.reflection.property;

import org.apache.ibatis.reflection.Reflector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class PropertyCopierTest {

    private SourceBean sourceBean;
    private DestinationBean destinationBean;

    @BeforeEach
    public void setUp() {
        sourceBean = new SourceBean();
        sourceBean.setPublicField("publicValue");
        sourceBean.setProtectedField("protectedValue");
        sourceBean.setPrivateField("privateValue");
        //sourceBean.setFinalField("finalValue"); // Final field is immutable and not copied
        sourceBean.setStaticField("staticValue"); // Static field is not instance-specific and not copied

        destinationBean = new DestinationBean();
    }

    @Test
    public void copyBeanProperties_AllFieldsCopiedCorrectly() {
        PropertyCopier.copyBeanProperties(SourceBean.class, sourceBean, destinationBean);

        assertEquals("publicValue", destinationBean.getPublicField());
        assertEquals("protectedValue", destinationBean.getProtectedField());
        assertEquals("privateValue", destinationBean.getPrivateField());
        // Final fields are immutable and not copied
        // Static fields are class-level and not copied
    }

    @Test
    public void copyBeanProperties_InheritedFieldsCopied() {
        PropertyCopier.copyBeanProperties(ChildBean.class, new ChildBean(), destinationBean);

        assertEquals("childValue", destinationBean.getPublicField());
    }

    static class SourceBean {
        private String privateField;
        protected String protectedField;
        public String publicField;
        final String finalField = "finalValue"; // Immutable and not copied
        static String staticField = "staticValue"; // Class-level and not copied

        public String getPrivateField() {
            return privateField;
        }

        public void setPrivateField(String privateField) {
            this.privateField = privateField;
        }

        public String getProtectedField() {
            return protectedField;
        }

        public void setProtectedField(String protectedField) {
            this.protectedField = protectedField;
        }

        public String getPublicField() {
            return publicField;
        }

        public void setPublicField(String publicField) {
            this.publicField = publicField;
        }

        public static String getStaticField() {
            return staticField;
        }

        public static void setStaticField(String staticField) {
            SourceBean.staticField = staticField;
        }
    }

    static class DestinationBean {
        private String privateField;
        protected String protectedField;
        public String publicField;
        final String finalField = null; // Immutable and not copied
        static String staticField = null; // Class-level and not copied

        public String getPrivateField() {
            return privateField;
        }

        public void setPrivateField(String privateField) {
            this.privateField = privateField;
        }

        public String getProtectedField() {
            return protectedField;
        }

        public void setProtectedField(String protectedField) {
            this.protectedField = protectedField;
        }

        public String getPublicField() {
            return publicField;
        }

        public void setPublicField(String publicField) {
            this.publicField = publicField;
        }

        public static String getStaticField() {
            return staticField;
        }

        public static void setStaticField(String staticField) {
            DestinationBean.staticField = staticField;
        }
    }

    static class ChildBean extends SourceBean {
        {
            publicField = "childValue";
        }
    }
}
