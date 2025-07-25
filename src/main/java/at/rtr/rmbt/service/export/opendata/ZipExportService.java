package at.rtr.rmbt.service.export.opendata;

import at.rtr.rmbt.mapper.OpenTestMapper;
import at.rtr.rmbt.repository.OpenTestExportRepository;
import at.rtr.rmbt.response.OpenTestExportDto;
import at.rtr.rmbt.service.FileService;
import org.apache.poi.util.IOUtils;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class ZipExportService extends CsvExportService {
    private static final String FILENAME_ZIP_HOURS = OPENDATA_FILENAME_PREFIX + "_hours-%HOURS%.zip";
    private static final String FILENAME_ZIP = OPENDATA_FILENAME_PREFIX + "-%YEAR%-%MONTH%.zip";
    private static final String FILENAME_ZIP_CURRENT = OPENDATA_FILENAME_PREFIX + ".zip";

    private final ResourceLoader resourceLoader;

    public ZipExportService(ResourceLoader resourceLoader,
                            OpenTestExportRepository openTestExportRepository,
                            OpenTestMapper openTestMapper,
                            FileService fileService) {
        super(openTestExportRepository,
                openTestMapper,
                fileService);
        this.resourceLoader = resourceLoader;
    }

    @Override
    protected void writeCustomLogic(List<OpenTestExportDto> results, OutputStream out, String fileName) throws IOException {
        final ZipOutputStream zos = new ZipOutputStream(out);
        final ZipEntry zeLicense = new ZipEntry("LICENSE.txt");
        zos.putNextEntry(zeLicense);
        final InputStream licenseIS = resourceLoader.getResource("classpath:png/DATA_LICENSE.txt").getInputStream();
        IOUtils.copy(licenseIS, zos);
        licenseIS.close();

        final ZipEntry zeCsv = new ZipEntry(fileName.replace("zip", "csv"));
        zos.putNextEntry(zeCsv);
        out = zos;
        super.writeCustomLogic(results, out, fileName);
        out.close();
    }

    @Override
    protected MediaType getMediaType() {
        return new MediaType("application", "zip");
    }

    @Override
    protected String getFileNameHours() {
        return FILENAME_ZIP_HOURS;
    }

    @Override
    protected String getFileName() {
        return FILENAME_ZIP;
    }

    @Override
    protected String getFileNameCurrent() {
        return FILENAME_ZIP_CURRENT;
    }

    @Override
    protected void setContentDisposition(ResponseEntity.BodyBuilder responseEntity, String filename) {
        responseEntity
                .header("Content-Disposition", "attachment; filename=" + filename);
    }
}
