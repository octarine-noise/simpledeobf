### What is this?

**simpledeobf** is a very simple and primitive command-line deobfuscator for
Minecraft mods. You can use it to create dev versions of mod releases
for debugging. Or anything else, really - it all depends on what inputs and
mapping files you use. Feel free to get creative with it.

### How to use it?

simpledeobf will need files from your Gradle cache which are created by
ForgeGradle, so it's best to start with a ForgeGradle workspace already
set up for your target Minecraft version.

Command-line options:

* `--output` The path to the resulting jar. You can only have one.
* `--input` The input jar you wish to deobfuscate. Can be repeated, but the
files from all inputs will just end up in a single output jar.
* `--mapFile` The path to the MCP mapping file, found in your Gradle cache
somewhere under `minecraft/de/oceanlabs/mcp`. You need to use the one that
corresponds to the namespaces you are converting from and to, and also
matches the ones you use in your project. Can be repeated, later files will
overwrite mappings from previous ones.
* `--map` Defines a single explicit mapping. This option is treated as if
it was a line in the MCP mapping file. Takes precedence over mappings
read from MCP files. Can be repeated.
* `--ref` The path to a reference jar. Can be repeated. The classes inside
are read only to determine the class hierarchy, which may be needed to properly
deobfuscate certain jar files. The reference jar must be in the same namespace
as the input.

   More precisely: you will need to provide a proper class hierarchy
to deobfuscate overridden methods whose declaring classes or interfaces are
2 or more levels above the overriding class.

   When in doubt, try using a Minecraft jar of the proper namespace.
* `--defaultPkg` move all classes from the default package into this one. Having
classes in the default package can mess with source attachment in the IDE.
* `--forcePublic` make all fields, methods and inner classes public. The
poor man's access transformer.
* `--xdeltaPrefix` Prefix for xdelta files.
* `--xdeltaPostfix` Postfix for xdelta files. Must be used together with the
previous option.

   Files that match the given pre/postfixes will be interpreted as patches.
   The same filename with pre/postfixes stripped from it will be searched for
   in the reference jars. If found, the patch will be applied to this file
   and the result used as if the patched version had been read from the
   input jar.
* `--help` or `-?` displays a quick overview of these options.

Have your favourite brand of decompiler ready, and be prepared to dig through
the result, and make several iterations before you end up with something
usable.

Also there's no guarantee that it's even possible to create a workable dev jar
for any given mod. If it uses reflection and/or class transformation, chances
are good that it will just crash anyway, unless it's specifically made to be
environment agnostic.

### Have OptiFine, will debug

As an example, here is a guide on getting OptiFine into your dev
environment with simpledeobf. This is *the* prime use-case of simpledeobf,
and the reason for its existence.

The following is the actual command I use for the deobfuscation itself,
so you'll need to change the directory names. It's a single command, but I
broke it up into lines for readability.
```
java -jar simpledeobf-0.5.jar
--input h:\Minecraft\mods\obf\OptiFine_1.8.8_HD_U_H2.jar
--output h:\Minecraft\mods\mcp\OptiFine_1.8.8_HD_U_H2-dev.jar
--mapFile h:\Minecraft\.gradle\caches\minecraft\de\oceanlabs\mcp\mcp_stable\20\srgs\notch-mcp.srg
--ref h:\Minecraft\.gradle\caches\minecraft\net\minecraft\minecraft_merged\1.8.8\minecraft_merged-1.8.8.jar
--map="CL: bet$1 net/minecraft/client/entity/AbstractClientPlayer$1"
--map="CL: b$8 net/minecraft/crash/CrashReport$8"
--forcePublic
```
This will give you a jar with the `stable_20` mappings. The 2 `map` options are
needed because OptiFine declares some extra inner classes that are not present
in vanilla and have no mappings. If you want to do this for a different Minecraft
version, you'll have to change these. Just start without manual mappings, and
check if there are still obfuscated classes in the result. Check in the MCP
files what the outer class is, and add a mapping. Rinse and repeat.

Open the resulting jar file, and delete the `net/minecraftforge` directory.
There are dummy classes inside that are normally not loaded, but will cause
problems in a dev environment. They need to go.

Now you have to create a tweaker and class transformer, because the default
OptiFine ones will not work properly in a dev environment.
[Here is mine](https://github.com/octarine-noise/BetterFoliage/blob/c0be72bb37311508c68db5bd3b09d2f99a76614c/src/main/kotlin/optifine/OptifineTweakerDevWrapper.kt)
that I use in Better Foliage, you need something similar. The point is to
change dots to slashes in the class names, so the OptiFine transformer can find
the deobfuscated class files.

Edit the `META-INF/MANIFEST.MF` file. Change the `TweakClass` option to the
tweaker you just made.

The jar is now ready. If you also want source attachment, which is highly
recommended, just decompile it with your favourite tool (I prefer JD-GUI), and
save a source jar. Make sure that *"Realign line numbers"* or the equivalent
option is turned on.

Drop the jar in the mods folder of your workspace, manually add it to the project
dependencies after all the Gradle stuff, set its source attachment, and you're
ready. You can start your project with OptiFine thrown in the mix, debug,
set breakpoints, and everything.

**Note:** Debugging *into* one of the vanilla classes that OptiFine overwrites
may or may not give you some headache under Eclipse. I can only confirm it works
fine under IDEA, which allows you to switch between sources on the fly if multiple
jar files declare a class. A popup comes up saying *"Alternative source available
for the class blah blah blah"*, and you can switch to the OptiFine jar, which
contains the class actually executing.

### OptiFine H5 and beyond

Starting with release H5, OptiFine has adopted a patch-based approach. Instead of
replacing the impacted class files with alternative ones, it supplies patches in
the *xdelta* format that are applied at runtime.

Instead of transforming xdelta files, simpledeobf is able to create classic
replacement-style deobfuscated jar files by applying the patches against the
original Minecraft jar. Again, you'll need to create a class transformer that is
able to load it [like this](https://github.com/octarine-noise/BetterFoliage/blob/abf037d8a9640594a76b9f2524885da6f440bd41/src/main/kotlin/optifine/OptifineTweakerDevWrapper.kt).

Here is the example command line I use to deobfuscate OptiFine for 1.9.
```
java -jar simpledeobf-0.6.jar
--input h:\Minecraft\mods\obf\OptiFine_1.9.0_HD_U_B2_pre.jar
--output h:\Minecraft\mods\mcp\OptiFine_1.9.0_HD_U_B2_pre-dev.jar
--mapFile h:\Minecraft\.gradle\caches\minecraft\de\oceanlabs\mcp\mcp_snapshot\20160406\srgs\notch-mcp.srg
--ref h:\Minecraft\.gradle\caches\minecraft\net\minecraft\minecraft\1.9\minecraft-1.9.jar
--forcePublic
--xdeltaPrefix="patch/"
--xdeltaPostfix=".xdelta"
```

Note the use of the regular `minecraft` binary, instead of `minecraft_merged`.
