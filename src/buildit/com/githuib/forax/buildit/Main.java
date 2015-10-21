package com.githuib.forax.buildit;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.lang.reflect.Layer;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

public class Main {
  private static final ModuleReader MODULE_READER = new ModuleReader() {
    @Override
    public Optional<InputStream> open(String name) {
      return Optional.empty();
    }
    @Override
    public void close() {
      // empty
    }
  };

  private static ModuleReference moduleReference(ModuleDescriptor descriptor, URI location) {
    return new ModuleReference(descriptor, location) {
      @Override
      public ModuleReader open() throws IOException {
        return MODULE_READER;
      }
    };
  }

  private static class SourceFileModuleFinder implements ModuleFinder {
      private final Path rootPath;

      SourceFileModuleFinder(Path rootPath) {
        this.rootPath = Objects.requireNonNull(rootPath);
      }

      @Override
      public Optional<ModuleReference> find(String name) {
        Path modulePath = rootPath.resolve(name);
        ModuleDescriptor descriptor;
        try {
          descriptor = ModuleParser.parse(modulePath.resolve("module-info.java"));
        } catch (IOException e) {
          return Optional.empty();
        }
        return Optional.of(moduleReference(descriptor, modulePath.toUri()));
      }

      @Override
      public Set<ModuleReference> findAll() {
        try {
          return Files.list(rootPath).flatMap(p -> find(p.getFileName().toString()).stream()).collect(toSet());
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }
    }

    public static void main(String[] args) {
      SourceFileModuleFinder moduleFinder = new SourceFileModuleFinder(Paths.get("example-src"));
      Configuration cf =
          Configuration.resolve(ModuleFinder.empty(),
              Layer.boot(),
              moduleFinder,
              moduleFinder.findAll().stream().map(ref -> ref.descriptor().name()).collect(toList()))
          .bind();

      System.out.println(cf.modules());
    }
}
