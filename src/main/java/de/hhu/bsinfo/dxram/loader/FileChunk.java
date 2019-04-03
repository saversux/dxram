package de.hhu.bsinfo.dxram.loader;

import de.hhu.bsinfo.dxmem.data.AbstractChunk;
import de.hhu.bsinfo.dxutils.serialization.Exporter;
import de.hhu.bsinfo.dxutils.serialization.Importer;
import lombok.Getter;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileChunk extends AbstractChunk {
    @Getter
    private String m_name;
    private int m_size;
    private byte[] m_fileArray;

    /**
     * Chunk that holds a file
     * @param p_file
     */
    public FileChunk(File p_file) {
        m_name = p_file.getName();
        m_size = (int) p_file.length();
        m_fileArray = fileToByte(p_file);
    }

    public FileChunk() {
        m_name = "";
    }

    /**
     * Saves file from chunk to desired destination
     * @param p_destDir destination directory
     * @return
     */
    public Path getFile(Path p_destDir) {
        Path jarPath = null;
        try {
            jarPath = Files.write(p_destDir, m_fileArray);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return jarPath;
    }

    /**
     * Converts file to byte array to save as chunk
     * @param p_file
     * @return
     */
    private byte[] fileToByte(File p_file) {
        byte[] fileBytes = new byte[(int)p_file.length()];
        try (FileInputStream fi = new FileInputStream(p_file)) {
            fi.read(fileBytes);
        }catch(FileNotFoundException e) {
            e.printStackTrace();
        }catch(IOException e) {
            e.printStackTrace();
        }

        return fileBytes;
    }

    @Override
    public void exportObject(Exporter p_exporter) {
        p_exporter.writeString(m_name);
        p_exporter.writeInt(m_size);
        p_exporter.writeByteArray(m_fileArray);
    }

    @Override
    public void importObject(Importer p_importer) {
        m_name = p_importer.readString(m_name);
        m_size = p_importer.readInt(m_size);
        m_fileArray = p_importer.readByteArray(m_fileArray);
    }

    @Override
    public int sizeofObject() {
        return m_name.length() * Character.BYTES + Integer.BYTES + m_size * Byte.BYTES - 3 ;
    }
}
