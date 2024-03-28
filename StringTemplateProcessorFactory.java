
public interface StringTemplateProcessorFactory {
    StringTemplateProcessor createProcessor(String[] fragments);
    default boolean cacheProcessor() {
        return true;
    }
}
