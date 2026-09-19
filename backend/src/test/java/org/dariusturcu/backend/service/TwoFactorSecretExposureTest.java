package org.dariusturcu.backend.service;

import org.dariusturcu.backend.model.TwoFactorBackupCode;
import org.dariusturcu.backend.model.user.User;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

// Every class Spring puts on the response classpath is scanned for a field literally named
// totpSecret or codeHash. The only classes allowed to declare either are the entities that
// store them; a DTO or response record that copied the field name onto itself, deliberately
// or by careless refactor, fails this test instead of shipping.
class TwoFactorSecretExposureTest {

    private static final List<String> SENSITIVE_FIELD_NAMES = List.of("totpSecret", "codeHash");

    @Test
    void noClassOtherThanTheOwningEntityDeclaresATotpSecretOrCodeHashField() throws Exception {
        Path classesRoot = mainClassesDirectory();

        try (Stream<Path> classFiles = Files.walk(classesRoot)) {
            List<Path> paths = classFiles.filter(path -> path.toString().endsWith(".class")).toList();
            for (Path classFile : paths) {
                Class<?> loadedClass = loadClass(classesRoot, classFile);
                for (Field field : loadedClass.getDeclaredFields()) {
                    if (!SENSITIVE_FIELD_NAMES.contains(field.getName())) {
                        continue;
                    }
                    assertThat(loadedClass)
                            .as("Field '%s' must only be declared by its owning entity, found on %s",
                                    field.getName(), loadedClass.getName())
                            .isIn(User.class, TwoFactorBackupCode.class);
                }
            }
        }
    }

    private Path mainClassesDirectory() throws URISyntaxException {
        URL location = User.class.getProtectionDomain().getCodeSource().getLocation();
        return new File(location.toURI()).toPath();
    }

    private Class<?> loadClass(Path classesRoot, Path classFile) throws ClassNotFoundException {
        String relativePath = classesRoot.relativize(classFile).toString();
        String className = relativePath
                .substring(0, relativePath.length() - ".class".length())
                .replace(File.separatorChar, '.');
        return Class.forName(className, false, getClass().getClassLoader());
    }
}
