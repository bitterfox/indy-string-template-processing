# indy-string-template-processing

## How to compile and run
This requires Java 22 + --enable-preview to compile and run since
- this relies on StringTemplate preview language feature
- this relies on ClassFileAPI preview API

```
$ javac --enable-preview -source 22

# Run ClassFileGenerator first to create Main.class with indy for StringTemplate Processor invocation
$ java --enable-preview ClassFileGenerator

# ClassFileGenerator generates Main.class in /tmp/Main.class
# Copy it to root dir of repo
$ cp /tmp/Main.class .

# Then run Main
$ java --enable-preview Main
Create new processor, some processor may parse fragments, so slow
Process string template
Hello duke
Process string template
Hello duke
Process string template
Hello duke

# Main calls doStringTemplate 3 times, and doStringTemplate runs StringTemplate expression
# However, you see "Create new processor, some processor may parse fragments, so slow" parsing of fragments are done only once in first call thanks to invokedynamic and caching

# We can change this behavior
# Modify StringTemplateSTRDebug.java like
$ git diff
diff --git a/StringTemplateSTRDebug.java b/StringTemplateSTRDebug.java
index 189627a..b7a2e23 100644
--- a/StringTemplateSTRDebug.java
+++ b/StringTemplateSTRDebug.java
@@ -15,6 +15,6 @@ public class StringTemplateSTRDebug implements StringTemplateProcessorFactory {
 
     @Override
     public boolean cacheProcessor() {
-        return true;
+        return false;
     }
 }

$ javac --enable-preview --source 22 StringTemplateSTRDebug.java
$ java --enable-preview Main
Create new processor, some processor may parse fragments, so slow
Process string template
Hello duke
Create new processor, some processor may parse fragments, so slow
Process string template
Hello duke
Create new processor, some processor may parse fragments, so slow
Process string template
Hello duke

# Now you see fragments are parsed 3 times (inefficient)
```

# How does it work

## StringTemplateProcessorFactory and StringTemplateProcessor
Unlike proposed StringTemplate.Processor in JEP 459, StringTemplateProcessorFactory responsibility is creating StringTemplateProcessor from fragments of StringTemplate.
StringTemplateProcessor does creating result of StringTemplate expression Object/T from Object[] (StringTemplate#values).
So I split the responsibility and thanks to it, we can separate the parsing StringTemplate#fragments and appling StringTemplate#values.

Parsing StringTemplate#fragments could be heavy than appling StringTemplate#value, so parsing fragments only once, and cache the parsed result is important to improve the performance.

Now we can cache StringTemplateProcessor for StringTemplate expression by paring StringTemplateRuntime to the StringTemplate expression and thanks to invokedynamic.
Overriding StringTemplateProcessorFactory#cacheProcessor let implementations to allow caching StringTemplateProcessor or not.

### StringTemplateSTRDebug
StringTemplateSTRDebug is a implementation of StringTemplate.STR with debug print
- when parsing fragments occureed (actually STR doesn't heavy work for fragments, but assume this is a StringTemplate for JSON or DB query builder)
- when processing values for the fragments occurred

## StringTemplateRuntime
StringTemplateRuntime calls StringTemplateProcessorFactory and StringTemplateProcessor for actual StringTemplate fragments and values.
StringTemplateRuntime cache StringTemplateProcessor if StringTemplateProcessorFactory allows it.

StringTemplateRuntime is created for each of StringTemplate expression.
So
- StringTemplateRuntime instance for `STR."Hello, \{name}"` caches StringTemplateProcessor created by STR.createProcessor(new String[] ("Hello, ", ""))
- StringTemplateRuntime instance for `STR."Java \{version}"` caches StringTemplateProcessor created by STR.createProcessor(new String[] ("Java ", ""))

StringTemplateRuntime is paired to the expression by invokedynamic and ConstantCallSite

## StringTemplateBSM
StringTemplateBSM is a BSM for invokedynamic call for StringTemplate expression.
- Create new StringTemplateBSM instance.
- Create MethodHandle for StringTemplateBSM#process(StringTemplateProcessorFactory, String[], Object[]): Object
- Bind the StringTemplateBSM instance to the MethodHandle receiver

## ClassFileGenerator
ClassFileGenerator generate Main.class using ClassFileAPI.
Assuming Main.java is
```
public class Main {
    public static void doStringTemplate(String[] args) throws Throwable {
        String name = "duke";
        System.out.println(StringTemplateSTRDebug.STR."Hello \{name}");
    }



    public static void main(String[] args) throws Throwable {
        doStringTemplate();
        doStringTemplate();
        doStringTemplate();
    }
}
```
(It's not fully corresponding byte codes to above codes)

Actual generated (human readable by javap) byte codes are
```
  Last modified 2024/03/28; size 829 bytes
  SHA-256 checksum 1c6fafab0b8e764729f468d554b8b001236b21a922b3ee8668fcbd84130e4daf
public class Main
  minor version: 0
  major version: 66
  flags: (0x0001) ACC_PUBLIC
  this_class: #2                          // Main
  super_class: #18                        // java/lang/Object
  interfaces: 0, fields: 0, methods: 2, attributes: 1
Constant pool:
   #1 = Utf8               Main
   #2 = Class              #1             // Main
   #3 = Utf8               doStringTemplate
   #4 = Utf8               ()V
   #5 = Utf8               StringTemplateSTRDebug
   #6 = Class              #5             // StringTemplateSTRDebug
   #7 = Utf8               STR
   #8 = Utf8               LStringTemplateProcessorFactory;
   #9 = NameAndType        #7:#8          // STR:LStringTemplateProcessorFactory;
  #10 = Fieldref           #6.#9          // StringTemplateSTRDebug.STR:LStringTemplateProcessorFactory;
  #11 = Utf8               java/lang/String
  #12 = Class              #11            // java/lang/String
  #13 = Utf8               Hello
  #14 = String             #13            // Hello
  #15 = Utf8
  #16 = String             #15            //
  #17 = Utf8               java/lang/Object
  #18 = Class              #17            // java/lang/Object
  #19 = Utf8               duke
  #20 = String             #19            // duke
  #21 = Utf8               StringTemplateBSM
  #22 = Class              #21            // StringTemplateBSM
  #23 = Utf8               createStringTemplateRuntimeCallSite
  #24 = Utf8               (Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;
  #25 = NameAndType        #23:#24        // createStringTemplateRuntimeCallSite:(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;
  #26 = Methodref          #22.#25        // StringTemplateBSM.createStringTemplateRuntimeCallSite:(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;
  #27 = MethodHandle       6:#26          // REF_invokeStatic StringTemplateBSM.createStringTemplateRuntimeCallSite:(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;
  #28 = Utf8               process
  #29 = Utf8               (LStringTemplateProcessorFactory;[Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;
  #30 = NameAndType        #28:#29        // process:(LStringTemplateProcessorFactory;[Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;
  #31 = InvokeDynamic      #0:#30         // #0:process:(LStringTemplateProcessorFactory;[Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;
  #32 = Utf8               java/lang/System
  #33 = Class              #32            // java/lang/System
  #34 = Utf8               out
  #35 = Utf8               Ljava/io/PrintStream;
  #36 = NameAndType        #34:#35        // out:Ljava/io/PrintStream;
  #37 = Fieldref           #33.#36        // java/lang/System.out:Ljava/io/PrintStream;
  #38 = Utf8               java/io/PrintStream
  #39 = Class              #38            // java/io/PrintStream
  #40 = Utf8               println
  #41 = Utf8               (Ljava/lang/Object;)V
  #42 = NameAndType        #40:#41        // println:(Ljava/lang/Object;)V
  #43 = Methodref          #39.#42        // java/io/PrintStream.println:(Ljava/lang/Object;)V
  #44 = Utf8               main
  #45 = Utf8               ([Ljava/lang/String;)V
  #46 = NameAndType        #3:#4          // doStringTemplate:()V
  #47 = Methodref          #2.#46         // Main.doStringTemplate:()V
  #48 = Utf8               Code
  #49 = Utf8               BootstrapMethods
{
  public static void doStringTemplate();
    descriptor: ()V
    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
    Code:
      stack=6, locals=2, args_size=0
         0: getstatic     #10                 // Field StringTemplateSTRDebug.STR:LStringTemplateProcessorFactory;
         3: iconst_2
         4: anewarray     #12                 // class java/lang/String
         7: dup
         8: iconst_0
         9: ldc           #14                 // String Hello
        11: aastore
        12: dup
        13: iconst_1
        14: ldc           #16                 // String
        16: aastore
        17: iconst_1
        18: anewarray     #18                 // class java/lang/Object
        21: dup
        22: iconst_0
        23: ldc           #20                 // String duke
        25: aastore
        26: invokedynamic #31,  0             // InvokeDynamic #0:process:(LStringTemplateProcessorFactory;[Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;
        31: astore_1
        32: getstatic     #37                 // Field java/lang/System.out:Ljava/io/PrintStream;
        35: aload_1
        36: invokevirtual #43                 // Method java/io/PrintStream.println:(Ljava/lang/Object;)V
        39: return

  public static void main(java.lang.String[]);
    descriptor: ([Ljava/lang/String;)V
    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
    Code:
      stack=0, locals=1, args_size=1
         0: invokestatic  #47                 // Method doStringTemplate:()V
         3: invokestatic  #47                 // Method doStringTemplate:()V
         6: invokestatic  #47                 // Method doStringTemplate:()V
         9: return
}
BootstrapMethods:
  0: #27 REF_invokeStatic StringTemplateBSM.createStringTemplateRuntimeCallSite:(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;
    Method arguments:
```
