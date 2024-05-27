package io.apitoolkit.spring.annotations;

import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;
import io.apitoolkit.spring.APIToolkitFilter;

public class APIToolkitImportSelector implements ImportSelector {
    @Override
    public String[] selectImports(AnnotationMetadata importingClassMetadata) {
        return new String[] { APIToolkitFilter.class.getName() };
    }
}