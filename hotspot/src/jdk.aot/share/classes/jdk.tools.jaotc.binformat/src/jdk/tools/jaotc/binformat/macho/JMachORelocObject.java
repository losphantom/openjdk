/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 *
 * File Layout generated by JMachORelocObject
 *
 * MachO Header
 * Load Commands
 *   LC_SEGMENT_64
 *    - Sections
 *   LC_VERSION_MIN_MAX
 *   LC_SYMTAB
 *   LC_DYSYMTAB
 * Section Data
 * Relocation entries
 * Symbol table
 *
 */

package jdk.tools.jaotc.binformat.macho;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import jdk.tools.jaotc.binformat.BinaryContainer;
import jdk.tools.jaotc.binformat.ByteContainer;
import jdk.tools.jaotc.binformat.CodeContainer;
import jdk.tools.jaotc.binformat.ReadOnlyDataContainer;
import jdk.tools.jaotc.binformat.Relocation;
import jdk.tools.jaotc.binformat.Relocation.RelocType;
import jdk.tools.jaotc.binformat.Symbol;
import jdk.tools.jaotc.binformat.NativeSymbol;
import jdk.tools.jaotc.binformat.Symbol.Binding;
import jdk.tools.jaotc.binformat.Symbol.Kind;

import jdk.tools.jaotc.binformat.macho.MachO;
import jdk.tools.jaotc.binformat.macho.MachO.section_64;
import jdk.tools.jaotc.binformat.macho.MachO.mach_header_64;
import jdk.tools.jaotc.binformat.macho.MachO.segment_command_64;
import jdk.tools.jaotc.binformat.macho.MachO.version_min_command;
import jdk.tools.jaotc.binformat.macho.MachO.symtab_command;
import jdk.tools.jaotc.binformat.macho.MachO.dysymtab_command;
import jdk.tools.jaotc.binformat.macho.MachO.nlist_64;
import jdk.tools.jaotc.binformat.macho.MachO.reloc_info;
import jdk.tools.jaotc.binformat.macho.MachOContainer;
import jdk.tools.jaotc.binformat.macho.MachOTargetInfo;
import jdk.tools.jaotc.binformat.macho.MachOSymtab;
import jdk.tools.jaotc.binformat.macho.MachORelocTable;

public class JMachORelocObject {

    private final BinaryContainer binContainer;

    private final MachOContainer machoContainer;

    private final int segmentSize;

    public JMachORelocObject(BinaryContainer binContainer, String outputFileName) {
        this.binContainer = binContainer;
        this.machoContainer = new MachOContainer(outputFileName);
        this.segmentSize = binContainer.getCodeSegmentSize();
    }

    private void createByteSection(ArrayList<MachOSection>sections,
                                   ByteContainer c, String sectName, String segName, int scnFlags) {

        if (c.getByteArray().length == 0) {
            // System.out.println("Skipping creation of " + sectName + " section, no data\n");
        }

        MachOSection sect = new MachOSection(sectName,
                                             segName,
                                             c.getByteArray(),
                                             scnFlags,
                                             c.hasRelocations(),
                                             segmentSize);
        // Add this section to our list
        sections.add(sect);

        // Record the section Id (0 relative)
        c.setSectionId(sections.size()-1);

        // TODO: Clear out code section data to allow for GC
        // c.clear();
    }

    private void createCodeSection(ArrayList<MachOSection>sections, CodeContainer c) {
        createByteSection(sections, c, /*c.getContainerName()*/ "__text", "__TEXT",
                          section_64.S_ATTR_PURE_INSTRUCTIONS|
                          section_64.S_ATTR_SOME_INSTRUCTIONS);
    }

    private void createReadOnlySection(ArrayList<MachOSection>sections, ReadOnlyDataContainer c) {
        createByteSection(sections, c, c.getContainerName(), "__TEXT",
                          section_64.S_ATTR_SOME_INSTRUCTIONS);
    }

    private void createReadWriteSection(ArrayList<MachOSection>sections, ByteContainer c) {
        createByteSection(sections, c, c.getContainerName(), "__DATA", section_64.S_REGULAR);
    }

    /**
     * Create an MachO relocatable object
     *
     * @param relocationTable
     * @param symbols
     * @throws IOException throws {@code IOException} as a result of file system access failures.
     */
    public void createMachORelocObject(Map<Symbol, List<Relocation>> relocationTable, Collection<Symbol> symbols) throws IOException {
        // Allocate MachO Header
        // with 4 load commands
        //   LC_SEGMENT_64
        //   LC_VERSION_MIN_MACOSX
        //   LC_SYMTAB
        //   LC_DYSYMTAB

        MachOHeader mh = new MachOHeader();

        ArrayList<MachOSection> sections = new ArrayList<MachOSection>();

        // Create Sections contained in the main Segment LC_SEGMENT_64

        createCodeSection(sections, binContainer.getCodeContainer());
        createReadOnlySection(sections, binContainer.getMetaspaceNamesContainer());
        createReadOnlySection(sections, binContainer.getKlassesOffsetsContainer());
        createReadOnlySection(sections, binContainer.getMethodsOffsetsContainer());
        createReadOnlySection(sections, binContainer.getKlassesDependenciesContainer());
        createReadWriteSection(sections, binContainer.getMetaspaceGotContainer());
        createReadWriteSection(sections, binContainer.getMetadataGotContainer());
        createReadWriteSection(sections, binContainer.getMethodStateContainer());
        createReadWriteSection(sections, binContainer.getOopGotContainer());
        createReadWriteSection(sections, binContainer.getMethodMetadataContainer());
        createReadOnlySection(sections, binContainer.getStubsOffsetsContainer());
        createReadOnlySection(sections, binContainer.getHeaderContainer().getContainer());
        createReadOnlySection(sections, binContainer.getCodeSegmentsContainer());
        createReadOnlySection(sections, binContainer.getConstantDataContainer());
        createReadOnlySection(sections, binContainer.getConfigContainer());

        // createExternalLinkage();

        createCodeSection(sections, binContainer.getExtLinkageContainer());
        createReadWriteSection(sections, binContainer.getExtLinkageGOTContainer());
        // Update the Header sizeofcmds size.
        // This doesn't include the Header struct size
        mh.setCmdSizes(4, segment_command_64.totalsize +
                          (section_64.totalsize * sections.size()) +
                          version_min_command.totalsize +
                          symtab_command.totalsize +
                          dysymtab_command.totalsize);

        // Initialize file offset for data past commands
        int file_offset = mach_header_64.totalsize + mh.getCmdSize();
        // and round it up
        file_offset = (file_offset + (sections.get(0).getAlign()-1)) & ~((sections.get(0).getAlign()-1));
        long address = 0;
        int segment_offset = file_offset;

        for (int i = 0; i < sections.size(); i++) {
            MachOSection sect = sections.get(i);
            file_offset = (file_offset + (sect.getAlign()-1)) & ~((sect.getAlign()-1));
            address = (address + (sect.getAlign()-1)) & ~((sect.getAlign()-1));
            sect.setOffset(file_offset);
            sect.setAddr(address);
            file_offset += sect.getSize();
            address += sect.getSize();
        }

        // File size for Segment data
        int segment_size = file_offset - segment_offset;

        // Create the LC_SEGMENT_64 Segment which contains the MachOSections
        MachOSegment seg = new MachOSegment(segment_command_64.totalsize +
                                            (section_64.totalsize * sections.size()),
                                            segment_offset,
                                            segment_size,
                                            sections.size());


        MachOVersion vers = new MachOVersion();

        // Get symbol data from BinaryContainer object's symbol tables
        MachOSymtab symtab = createMachOSymbolTables(sections, symbols);

        // Create LC_DYSYMTAB command
        MachODySymtab dysymtab = new MachODySymtab(symtab.getNumLocalSyms(),
                                                   symtab.getNumGlobalSyms(),
                                                   symtab.getNumUndefSyms());

        // Create the Relocation Tables
        MachORelocTable machORelocs = createMachORelocTable(sections, relocationTable, symtab);
        // Calculate file offset for relocation data
        file_offset = (file_offset + (machORelocs.getAlign()-1)) & ~((machORelocs.getAlign()-1));

        // Update relocation sizing information in each section
        for (int i = 0; i < sections.size(); i++) {
            MachOSection sect = sections.get(i);
            if (sect.hasRelocations()) {
                int nreloc = machORelocs.getNumRelocs(i);
                sect.setReloff(file_offset);
                sect.setRelcount(nreloc);
                file_offset += (nreloc * reloc_info.totalsize);
            }
        }

        // Calculate and set file offset for symbol table data
        file_offset = (file_offset + (symtab.getAlign()-1)) & ~((symtab.getAlign()-1));
        symtab.setOffset(file_offset);


        // Write Out Header
        machoContainer.writeBytes(mh.getArray());
        // Write out first Segment
        machoContainer.writeBytes(seg.getArray());
        // Write out sections within first Segment
        for (int i = 0; i < sections.size(); i++) {
            MachOSection sect = sections.get(i);
            machoContainer.writeBytes(sect.getArray());
        }

        // Write out LC_VERSION_MIN_MACOSX command
        machoContainer.writeBytes(vers.getArray());

        // Write out LC_SYMTAB command
        symtab.calcSizes();
        machoContainer.writeBytes(symtab.getCmdArray());

        // Write out LC_DYSYMTAB command
        machoContainer.writeBytes(dysymtab.getArray());

        // Write out data associated with each Section
        for (int i = 0; i < sections.size(); i++) {
            MachOSection sect = sections.get(i);
            machoContainer.writeBytes(sect.getDataArray(), sect.getAlign());
        }

        // Write out the relocation tables for all sections
        for (int i = 0; i < sections.size(); i++) {
            if (machORelocs.getNumRelocs(i) > 0)
                machoContainer.writeBytes(machORelocs.getRelocData(i), machORelocs.getAlign());
        }

        // Write out data associated with LC_SYMTAB
        machoContainer.writeBytes(symtab.getDataArray(), symtab.getAlign());

        machoContainer.close();
    }

    /**
     * Construct MachO symbol data from BinaryContainer object's symbol tables. Both dynamic MachO
     * symbol table and MachO symbol table are created from BinaryContainer's symbol info.
     *
     * @param symbols
     * @param symtab
     */
    private MachOSymtab createMachOSymbolTables(ArrayList<MachOSection>sections,
                                         Collection<Symbol> symbols) {
        MachOSymtab symtab = new MachOSymtab();
        // First, create the initial null symbol. This is a local symbol.
        symtab.addSymbolEntry("", (byte)nlist_64.N_UNDF, (byte)0, (long)0);

        // Now create MachO symbol entries for all symbols.
        for (Symbol symbol : symbols) {
            int sectionId = symbol.getSection().getSectionId();

            // Symbol offsets are relative to the section memory addr
            long sectionAddr = sections.get(sectionId).getAddr();

            MachOSymbol machoSymbol = symtab.addSymbolEntry(symbol.getName(),
                                         getMachOTypeOf(symbol),
                                         (byte)sectionId,
                                         symbol.getOffset() + sectionAddr);
            symbol.setNativeSymbol((NativeSymbol)machoSymbol);
        }

        // Now that all symbols are enterred, update the
        // symbol indexes.  This is necessary since they will
        // be reordered based on local, global and undefined.
        symtab.updateIndexes();

        return (symtab);
    }

    private static byte getMachOTypeOf(Symbol sym) {
        Kind kind = sym.getKind();
        byte type = nlist_64.N_UNDF;

        // Global or Local
        if (sym.getBinding() == Symbol.Binding.GLOBAL)
            type = nlist_64.N_EXT;

        // If Function or Data, add section type
        if (kind == Symbol.Kind.NATIVE_FUNCTION ||
            kind == Symbol.Kind.JAVA_FUNCTION   ||
            kind == Symbol.Kind.OBJECT) {
            type |= (nlist_64.N_SECT);
        }

        return (type);
    }

    /**
     * Construct a MachO relocation table from BinaryContainer object's relocation tables.
     *
     * @param sections
     * @param relocationTable
     * @param symtab
     */
    private MachORelocTable createMachORelocTable(ArrayList<MachOSection> sections,
                                                  Map<Symbol, List<Relocation>> relocationTable,
                                                  MachOSymtab symtab) {

        MachORelocTable machORelocTable = new MachORelocTable(sections.size());
        /*
         * For each of the symbols with associated relocation records, create a MachO relocation
         * entry.
         */
        for (Map.Entry<Symbol, List<Relocation>> entry : relocationTable.entrySet()) {
            List<Relocation> relocs = entry.getValue();
            Symbol symbol = entry.getKey();

            for (Relocation reloc : relocs) {
                createRelocation(symbol, reloc, machORelocTable);
            }
        }

        for (Map.Entry<Symbol, Relocation> entry : binContainer.getUniqueRelocationTable().entrySet()) {
            createRelocation(entry.getKey(), entry.getValue(), machORelocTable);
        }

        return (machORelocTable);
    }

    private void createRelocation(Symbol symbol, Relocation reloc, MachORelocTable machORelocTable) {
        RelocType relocType = reloc.getType();

        int machORelocType = getMachORelocationType(relocType);
        MachOSymbol sym = (MachOSymbol)symbol.getNativeSymbol();
        int symno = sym.getIndex();
        int sectindex = reloc.getSection().getSectionId();
        int offset = reloc.getOffset();
        int pcrel = 0;
        int length = 0;
        int isextern = 1;

/*
        System.out.println("reloctype: " + relocType + " size is " +
                            reloc.getSize() + " offset is " + offset +
                            " Section Index is " + (sectindex) +
                            " Symbol Index is " + symno +
                            " Symbol Name is " + symbol.getName() + "\n");
*/

        switch (relocType) {
            case FOREIGN_CALL_DIRECT:
            case JAVA_CALL_DIRECT:
            case STUB_CALL_DIRECT:
            case FOREIGN_CALL_INDIRECT_GOT: {
                // Create relocation entry
                // System.out.println("getMachORelocationType: PLT relocation type using X86_64_RELOC_BRANCH");
                int addend = -4; // Size in bytes of the patch location
                // Relocation should be applied at the location after call operand
                offset = offset + reloc.getSize() + addend;
                pcrel = 1; length = 2;
                break;
            }
            case FOREIGN_CALL_DIRECT_FAR: {
                // Create relocation entry
                int addend = -8; // Size in bytes of the patch location
                // Relocation should be applied at the location after call operand
                // 10 = 2 (jmp [r]) + 8 (imm64)
                offset = offset + reloc.getSize() + addend - 2;
                pcrel = 0; length = 3;
                break;
            }
            case FOREIGN_CALL_INDIRECT:
            case JAVA_CALL_INDIRECT:
            case STUB_CALL_INDIRECT: {
                // Do nothing.
                return;
            }
            case EXTERNAL_DATA_REFERENCE_FAR: {
                // Create relocation entry
                int addend = -4; // Size of 32-bit address of the GOT
                /*
                 * Relocation should be applied before the test instruction to the move instruction.
                 * offset points to the test instruction after the instruction that loads
                 * the address of polling page. So set the offset appropriately.
                 */
                offset = offset + addend;
                pcrel = 0; length = 2;
                break;
            }
            case METASPACE_GOT_REFERENCE:
            case EXTERNAL_PLT_TO_GOT:
            case STATIC_STUB_TO_STATIC_METHOD:
            case STATIC_STUB_TO_HOTSPOT_LINKAGE_GOT: {
                int addend = -4; // Size of 32-bit address of the GOT
                /*
                 * Relocation should be applied before the test instruction to
                 * the move instruction. reloc.getOffset() points to the
                 * test instruction after the instruction that loads the
                 * address of polling page. So set the offset appropriately.
                 */
                offset = offset + addend;
                pcrel = 1; length = 2;
                break;
            }
            case EXTERNAL_GOT_TO_PLT:
            case LOADTIME_ADDRESS: {
                // this is load time relocations
                pcrel = 0; length = 3;
                break;
            }
            default:
                throw new InternalError("Unhandled relocation type: " + relocType);
        }
        machORelocTable.createRelocationEntry(sectindex, offset, symno,
                                              pcrel, length, isextern,
                                              machORelocType);
    }

    private static int getMachORelocationType(RelocType relocType) {
        int machORelocType = 0;
        switch (MachOTargetInfo.getMachOArch()) {
            case mach_header_64.CPU_TYPE_X86_64:
                // Return X86_64_RELOC_* entries based on relocType
                if (relocType == RelocType.FOREIGN_CALL_DIRECT || relocType == RelocType.JAVA_CALL_DIRECT || relocType == RelocType.FOREIGN_CALL_INDIRECT_GOT) {
                    machORelocType = reloc_info.X86_64_RELOC_BRANCH;
                } else if (relocType == RelocType.STUB_CALL_DIRECT) {
                    machORelocType = reloc_info.X86_64_RELOC_BRANCH;
                } else if (relocType == RelocType.FOREIGN_CALL_DIRECT_FAR) {
                    machORelocType = reloc_info.X86_64_RELOC_UNSIGNED;
                } else if (relocType == RelocType.FOREIGN_CALL_INDIRECT || relocType == RelocType.JAVA_CALL_INDIRECT || relocType == RelocType.STUB_CALL_INDIRECT) {
                    machORelocType = reloc_info.X86_64_RELOC_NONE;
                } else if ((relocType == RelocType.EXTERNAL_DATA_REFERENCE_FAR)) {
                    machORelocType = reloc_info.X86_64_RELOC_GOT;
                } else if (relocType == RelocType.METASPACE_GOT_REFERENCE || relocType == RelocType.EXTERNAL_PLT_TO_GOT || relocType == RelocType.STATIC_STUB_TO_STATIC_METHOD ||
                                relocType == RelocType.STATIC_STUB_TO_HOTSPOT_LINKAGE_GOT) {
                    machORelocType = reloc_info.X86_64_RELOC_BRANCH;
                } else if (relocType == RelocType.EXTERNAL_GOT_TO_PLT || relocType == RelocType.LOADTIME_ADDRESS) {
                    machORelocType = reloc_info.X86_64_RELOC_UNSIGNED;
                } else {
                    assert false : "Unhandled relocation type: " + relocType;
                }
                break;
            default:
                System.out.println("Relocation Type mapping: Unhandled architecture");
        }
        return machORelocType;
    }
}
