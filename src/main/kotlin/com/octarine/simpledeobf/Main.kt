@file:JvmName("Main")
package com.octarine.simpledeobf

import com.nothome.delta.GDiffPatcher
import com.sun.xml.internal.messaging.saaj.util.ByteInputStream
import joptsimple.OptionException
import joptsimple.OptionParser
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

fun main(args : Array<String>) {

    try {
        val parser = OptionParser()
        val inputFile = parser.accepts("input", "Path to input JAR file").withRequiredArg().ofType(File::class.java).required()
        val outputFile = parser.accepts("output", "Path to output JAR file").withRequiredArg().ofType(File::class.java).required()
        val referenceFile = parser.accepts("ref", "Path to reference JAR file").withRequiredArg().ofType(File::class.java)
        val mappingFile = parser.accepts("mapFile", "Path to mapping file").withRequiredArg().ofType(File::class.java)
        val mapping = parser.accepts("map", "Manual mapping entry").withRequiredArg().ofType(String::class.java)
        val defaultPkg = parser.accepts("defaultPkg", "Map default package").withRequiredArg().ofType(String::class.java)
        val forcePublic = parser.accepts("forcePublic", "Force everything to be public")
        val xdeltaPrefix = parser.accepts("xdeltaPrefix", "prefix to strip").withOptionalArg().ofType(String::class.java)
        val xdeltaPostfix = parser.accepts("xdeltaPostfix", "postfix for xdelta (GDiff) files").withOptionalArg().ofType(String::class.java)
        val help = parser.acceptsAll(listOf("?", "help")).forHelp()

        val options = parser.parse(*args)

        if (options.has(help)) {
            parser.printHelpOn(System.out)
            System.exit(0)
        }

        if (options.valuesOf(outputFile).size != 1) {
            println("Maximum of 1 output file is allowed")
            System.exit(1)
        }

        if (options.valuesOf(defaultPkg).size > 1) {
            println("Maximum of 1 default package is allowed")
            System.exit(1)
        }

        if (options.has(xdeltaPrefix) != options.has(xdeltaPostfix)) {
            println("xdeltaPrefix and xdeltaPostfix need to be defined together")
            System.exit(1)
        }

        if (options.valuesOf(xdeltaPrefix).size > 1 || options.valuesOf(xdeltaPostfix).size > 1) {
            println("Only 1 xpatch prefix and postfix is allowed")
            System.exit(1)
        }

        val doPatchProcessing = options.valuesOf(xdeltaPrefix).size == 1

        val mapper = SimpleRemapper(options.valueOf(defaultPkg))
        options.valuesOf(mappingFile).forEach {
            println("Reading mappings from: ${it.absolutePath}")
            mapper.readMappingFile(it)
        }
        options.valuesOf(mapping).forEach { mapper.readMappingLine(it) }
        options.valuesOf(referenceFile).forEach {
            println("Reading hiererchy from: ${it.absolutePath}")
            mapper.hierarchyReader.visitAllFromFile(it)
        }
        options.valuesOf(inputFile).forEach {
            println("Reading hiererchy from: ${it.absolutePath}")
            mapper.hierarchyReader.visitAllFromFile(it)
        }

        val destJar = options.valuesOf(outputFile)[0].let {
            if (it.exists()) it.delete()
            ZipOutputStream(FileOutputStream(it))
        }

        fun writeDeobfedClass(srcBytes: ByteArray, name: String) {
            val srcClass = ClassNode().apply { ClassReader(srcBytes).accept(this, ClassReader.EXPAND_FRAMES) }
            val destClass = mapper.remapClass(srcClass, options.has(forcePublic))
            val destBytes = ClassWriter(0).apply { destClass.accept(this) }.toByteArray()

            println(if (!name.startsWith(destClass.name)) "-> ${destClass.name}.class" else "")
            destJar.putNextEntry(ZipEntry("${destClass.name}.class"))
            destJar.write(destBytes)
            destJar.closeEntry()
        }

        for (srcFile in options.valuesOf(inputFile)) {
            println("Processing input file: ${srcFile.absolutePath}")
            val srcJar = ZipFile(srcFile)
            for (srcEntry in srcJar.entries()) {
                var srcName = srcEntry.name
                var srcBytes = srcJar.getInputStream(srcEntry).readBytes()
                if (doPatchProcessing &&
                    srcName.endsWith(options.valueOf(xdeltaPostfix)!!) &&
                    srcName.startsWith(options.valueOf(xdeltaPrefix)!!)) {
                    // patch file - try to patch a matching file from a reference JAR
                    print("   patching: ${srcName}")
                    val patchBytes = srcBytes + 0   // make sure there's an EOF marker
                    val origName = srcEntry.name.subSequence(
                        options.valueOf(xdeltaPrefix)!!.length,
                        srcEntry.name.length - options.valueOf(xdeltaPostfix)!!.length
                    ) as String
                    val origBytes = (options.valuesOf(inputFile) + options.valuesOf(referenceFile)).map {
                        val refFile = ZipFile(it)
                        refFile.getEntry(origName)?.let { refFile.getInputStream(it).readBytes() }
                    }.filterNotNull().firstOrNull()
                    if (origBytes != null) {
                        srcName = origName
                        srcBytes = GDiffPatcher().patch(origBytes, patchBytes)
                        println(" -> $srcName")
                    } else {
                        println("")
                    }
                }
                if (srcName.endsWith(".class")) {
                    print("   processing: ${srcName} ")
                    writeDeobfedClass(srcBytes, srcEntry.name)
                } else {
                    println("   copying: ${srcName}")
                    destJar.putNextEntry(ZipEntry(srcName))
                    ByteInputStream(srcBytes, srcBytes.size).copyTo(destJar)
                    destJar.closeEntry()
                }
            }
        }

        println("Conversion finished")
        destJar.close()
    } catch(e: OptionException) {
        println(e.message)
        System.exit(1)
    } catch(e: FileNotFoundException) {
        println(e.message)
        System.exit(1)
    }
}