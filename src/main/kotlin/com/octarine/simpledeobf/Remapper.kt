package com.octarine.simpledeobf

import org.objectweb.asm.*
import org.objectweb.asm.commons.Remapper
import org.objectweb.asm.commons.RemappingClassAdapter
import org.objectweb.asm.tree.ClassNode
import java.io.File
import java.util.*
import java.util.zip.ZipFile

class SimpleRemapper(val defaultPkg: String?) : Remapper() {

    val String.partOwner: String get() = this.substring(0, this.lastIndexOf("/"))
    val String.partName: String get() = this.substring(this.lastIndexOf("/") + 1)

    val mappings = HashMap<String, ClassMapping>()
    val hierarchy = HashMap<String, ClassHierarchy>()
    val hierarchyReader = SimpleHierarchyReader()

    inner class SimpleHierarchyReader : ClassVisitor(Opcodes.ASM5) {
        override fun visit(version: Int, access: Int, name: String, signature: String?, superName: String?, interfaces: Array<out String>?) {
            hierarchy.put(name, ClassHierarchy(superName, interfaces))
        }

        fun visitAllFromFile(file: File) {
            val jarFile = ZipFile(file)
            for (srcEntry in jarFile.entries()) {
                if (srcEntry.name.endsWith(".class")) {
                    val srcBytes = jarFile.getInputStream(srcEntry).readBytes()
                    ClassReader(srcBytes).accept(this, ClassReader.EXPAND_FRAMES)
                }
            }
        }
    }

    fun remapClass(srcClass: ClassNode, forcePublic: Boolean) = ClassNode().apply {
        srcClass.accept(PublicAccessRemappingClassAdapter(this, this@SimpleRemapper, forcePublic))
    }

    fun readMappingFile(file: File) {
        if (!file.exists()) throw Exception("Mappings file doesn't exist: ${file.absolutePath}")
        file.readLines().forEach { readMappingLine(it) }
    }

    fun readMappingLine(line: String) {
        val tokens = line.split(" ")
        if (tokens[0] == "CL:") addClassMapping(tokens[1], tokens[2])
        if (tokens[0] == "FD:") addFieldMapping(tokens[1].partOwner, tokens[1].partName, tokens[2].partOwner, tokens[2].partName)
        if (tokens[0] == "MD:") addMethodMapping(tokens[1].partOwner, tokens[1].partName, tokens[2], tokens[3].partOwner, tokens[3].partName, tokens[4])
    }

    fun addClassMapping(fromName: String, toName: String) {
        mappings.put(fromName, ClassMapping(toName))
    }

    fun addFieldMapping(fromOwner: String, fromName: String, toOwner: String, toName: String) {
        mappings[fromOwner]?.fields?.put(fromName, toName)
    }

    fun addMethodMapping(fromOwner: String, fromName: String, fromDesc: String, toOwner: String, toName: String, toDesc: String) {
        mappings[fromOwner]?.methods?.put(fromName to fromDesc, toName)
    }

    override fun map(typeName: String): String? = (mappings[typeName]?.mappedName ?: typeName).let {
        if (it.contains("/") || defaultPkg == null) it else "$defaultPkg/$it"
    }

    override fun mapFieldName(owner: String, name: String, desc: String?): String? {
        mappings[owner]?.let { it.fields[name] }?.let { return it }
        hierarchy[owner]?.superName?.let { return mapFieldName(it, name, desc) }
        return name
    }

    override fun mapMethodName(owner: String, name: String, desc: String) =
        mapMethodNameInternal(owner, name, desc) ?: name

    fun mapMethodNameInternal(owner: String, name: String, desc: String): String? {
        mappings[owner]?.let { it.methods[name to desc] }?.let { return it }
        val h = hierarchy[owner] ?: return null
        if (h.superName != null) mapMethodNameInternal(h.superName, name, desc)?.let { return it }
        if (h.interfaces != null) for (interfaceName in h.interfaces) {
            mapMethodNameInternal(interfaceName, name, desc)?.let { return it }
        }
        return null
    }
}

class PublicAccessRemappingClassAdapter(cv: ClassVisitor, remapper: Remapper, val force: Boolean) : RemappingClassAdapter(cv, remapper) {

    val Int.toPublic: Int get() = (this and 0xFFF8) or 0x1

    override fun visitMethod(access: Int, name: String?, desc: String?, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
        return super.visitMethod(if (force) access.toPublic else access, name, desc, signature, exceptions)
    }

    override fun visitField(access: Int, name: String?, desc: String?, signature: String?, value: Any?): FieldVisitor? {
        return super.visitField(if (force) access.toPublic else access, name, desc, signature, value)
    }

    override fun visitInnerClass(name: String?, outerName: String?, innerName: String?, access: Int) {
        super.visitInnerClass(name, outerName, innerName, if (force) access.toPublic else access)
    }
}

class ClassMapping(val mappedName: String) {
    val fields = HashMap<String, String>()
    val methods = HashMap<Pair<String, String>, String>()
}

class ClassHierarchy(val superName: String?, val interfaces: Array<out String>?) {}