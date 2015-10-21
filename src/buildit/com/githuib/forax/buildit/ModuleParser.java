package com.githuib.forax.buildit;

import static com.githuib.forax.buildit.ModuleParser.Keyword.*;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.stream.Collectors.toMap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleDescriptor.Requires.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

public class ModuleParser {
  interface Token {
    Object value();

    static Token create(Object value) {
      return Keyword.MAP.computeIfAbsent(value, v -> () -> v);
    }
  }

  enum Keyword implements Token {
    module, requires, exports, _public("public"), provides, with, uses,
    id, lcurl('{'), rcurl('}'), semi(';');

    private final Object value;

    private Keyword() {
      this(null);
    }

    private Keyword(Object value) {
      this.value = value;
    }

    public Object value() {
      return (value == null) ? name() : value;
    }

    static final Map<Object, Token> MAP =
        Arrays.stream(Keyword.values()).filter(k -> k != id).collect(toMap(Keyword::value, k -> k));
  }

  static class Lexer {
    private final char[] buffer;
    private int position;
    private Token token;

    public Lexer(Reader reader) throws IOException {
      this.buffer = readAll(reader);
    }

    public String nextToken(Keyword keyword) {
      Token token = peekToken();
      this.token = null;
      boolean isKeyword = token instanceof Keyword;
      if ((isKeyword && token != keyword) || (!isKeyword && keyword != Keyword.id)) {
        throw new IllegalStateException("found " + token + " but ask for " + keyword);
      }
      return token.value().toString();
    }

    public Keyword lookAhead() {
      Token token = peekToken();
      return (token instanceof Keyword) ? (Keyword) token : Keyword.id;
    }

    private Token peekToken() {
      Token token = this.token;
      if (token == null) {
        this.token = token = pullToken();
      }
      return token;
    }

    private Token pullToken() {
      int start = position;
      for (; ; ) {
        char c = buffer[position];
        switch (c) {
          case ' ':
          case '\t':
          case '\n':
          case '\r':
            if (start != position) {
              return Token.create(new String(buffer, start, position++ - start));
            }
            start = ++position;
            break;

          case '{':
          case '}':
          case ';':
            if (start != position) {
              return Token.create(new String(buffer, start, position - start));
            }
            position++;
            return Token.create(c);
          default:
            position++;
        }
      }
    }

    private static char[] readAll(Reader reader) throws IOException {
      char[] buffer = new char[8192];
      int read = 0;
      for (; ; ) {
        int n;
        while ((n = reader.read(buffer, read, buffer.length - read)) > 0) {
          read += n;
        }
        if (n == -1) {
          break;
        }
        buffer = Arrays.copyOf(buffer, buffer.length << 1);
      }
      return (buffer.length == read) ? buffer : Arrays.copyOf(buffer, read);
    }
  }

  public static ModuleDescriptor parse(Path path) throws IOException {
    try (BufferedReader reader = Files.newBufferedReader(path)) {
      return parse(reader);
    }
  }

  public static ModuleDescriptor parse(Reader reader) throws IOException {
    return parseModule(new Lexer(reader));
  }

  private static ModuleDescriptor parseModule(Lexer lexer) throws IOException {
    lexer.nextToken(module);
    String name = lexer.nextToken(id);
    ModuleDescriptor.Builder builder = new ModuleDescriptor.Builder(name);
    lexer.nextToken(lcurl);
    parseDirectives(builder, lexer);
    lexer.nextToken(rcurl);
    return builder.build();
  }

  private static void parseDirectives(ModuleDescriptor.Builder builder, Lexer lexer) {
    for (; ; ) {
      Keyword lookAhead = lexer.lookAhead();
      switch (lookAhead) {
        case requires:
          lexer.nextToken(requires);
          boolean reExport = lexer.lookAhead() == _public;
          if (reExport) {
            lexer.nextToken(_public);
          }
          Set<Modifier> modifiers = reExport ? singleton(Modifier.PUBLIC) : emptySet();
          builder.requires(modifiers, lexer.nextToken(id));
          lexer.nextToken(semi);
          continue;
        case exports:
          lexer.nextToken(exports);
          builder.exports(lexer.nextToken(id));
          lexer.nextToken(semi);
          continue;
        case provides:
          lexer.nextToken(provides);
          String service = lexer.nextToken(id);
          lexer.nextToken(with);
          String implementation = lexer.nextToken(id);
          builder.provides(service, implementation);
          lexer.nextToken(semi);
          continue;
        case uses:
          lexer.nextToken(uses);
          builder.uses(lexer.nextToken(id));
          lexer.nextToken(semi);
          continue;
        default:
          return;
      }
    }
  }
}
