package sciens.cyrodracs.camera;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class NikonCameraInsertTest {

    @Autowired CameraRepository cameraRepo;
    @Autowired CameraProducerRepository producerRepo;

    private record CameraDef(String name, int releaseYear) {}

    // ---- DSLR cameras (55 models) ----
    private static final List<CameraDef> NIKON_DSLR = List.of(
            new CameraDef("Nikon D1", 1999),
            new CameraDef("Nikon D1H", 2001),
            new CameraDef("Nikon D1X", 2001),
            new CameraDef("Nikon D100", 2002),
            new CameraDef("Nikon D2H", 2003),
            new CameraDef("Nikon D70", 2004),
            new CameraDef("Nikon D2X", 2004),
            new CameraDef("Nikon D2Hs", 2005),
            new CameraDef("Nikon D50", 2005),
            new CameraDef("Nikon D70s", 2005),
            new CameraDef("Nikon D200", 2005),
            new CameraDef("Nikon D80", 2006),
            new CameraDef("Nikon D2Xs", 2006),
            new CameraDef("Nikon D40", 2006),
            new CameraDef("Nikon D40X", 2007),
            new CameraDef("Nikon D3", 2007),
            new CameraDef("Nikon D300", 2007),
            new CameraDef("Nikon D60", 2008),
            new CameraDef("Nikon D700", 2008),
            new CameraDef("Nikon D90", 2008),
            new CameraDef("Nikon D3X", 2008),
            new CameraDef("Nikon D5000", 2009),
            new CameraDef("Nikon D3000", 2009),
            new CameraDef("Nikon D300S", 2009),
            new CameraDef("Nikon D3S", 2009),
            new CameraDef("Nikon D3100", 2010),
            new CameraDef("Nikon D7000", 2010),
            new CameraDef("Nikon D5100", 2011),
            new CameraDef("Nikon D4", 2012),
            new CameraDef("Nikon D800", 2012),
            new CameraDef("Nikon D800E", 2012),
            new CameraDef("Nikon D600", 2012),
            new CameraDef("Nikon D3200", 2012),
            new CameraDef("Nikon D5200", 2012),
            new CameraDef("Nikon D7100", 2013),
            new CameraDef("Nikon D610", 2013),
            new CameraDef("Nikon D5300", 2013),
            new CameraDef("Nikon Df", 2013),
            new CameraDef("Nikon D3300", 2014),
            new CameraDef("Nikon D4S", 2014),
            new CameraDef("Nikon D750", 2014),
            new CameraDef("Nikon D810", 2014),
            new CameraDef("Nikon D810A", 2015),
            new CameraDef("Nikon D5500", 2015),
            new CameraDef("Nikon D7200", 2015),
            new CameraDef("Nikon D500", 2016),
            new CameraDef("Nikon D5", 2016),
            new CameraDef("Nikon D3400", 2016),
            new CameraDef("Nikon D5600", 2016),
            new CameraDef("Nikon D7500", 2017),
            new CameraDef("Nikon D850", 2017),
            new CameraDef("Nikon D3500", 2018),
            new CameraDef("Nikon D780", 2020),
            new CameraDef("Nikon D6", 2020)
    );

    // ---- Mirrorless cameras (25 models) ----
    private static final List<CameraDef> NIKON_MIRRORLESS = List.of(
            // Nikon 1 series
            new CameraDef("Nikon 1 J1", 2011),
            new CameraDef("Nikon 1 J2", 2012),
            new CameraDef("Nikon 1 J3", 2013),
            new CameraDef("Nikon 1 J4", 2014),
            new CameraDef("Nikon 1 J5", 2015),
            new CameraDef("Nikon 1 V1", 2011),
            new CameraDef("Nikon 1 V2", 2012),
            new CameraDef("Nikon 1 V3", 2014),
            new CameraDef("Nikon 1 S1", 2013),
            new CameraDef("Nikon 1 S2", 2014),
            new CameraDef("Nikon 1 AW1", 2013),
            // Z-mount DX
            new CameraDef("Nikon Z50", 2019),
            new CameraDef("Nikon Z30", 2022),
            new CameraDef("Nikon Zfc", 2021),
            new CameraDef("Nikon Z50 II", 2024),
            // Z-mount FX
            new CameraDef("Nikon Z5", 2020),
            new CameraDef("Nikon Z6", 2018),
            new CameraDef("Nikon Z7", 2018),
            new CameraDef("Nikon Z6II", 2020),
            new CameraDef("Nikon Z7II", 2020),
            new CameraDef("Nikon Z6III", 2024),
            new CameraDef("Nikon Z8", 2023),
            new CameraDef("Nikon Z9", 2021),
            new CameraDef("Nikon Zf", 2023),
            new CameraDef("Nikon Z5II", 2025)
    );

    // ---- Film SLR cameras (57 models) ----
    private static final List<CameraDef> NIKON_FILM = List.of(
            // F professional series
            new CameraDef("Nikon F", 1959),
            new CameraDef("Nikon F2", 1971),
            new CameraDef("Nikon F2 Photomic", 1971),
            new CameraDef("Nikon F2S", 1973),
            new CameraDef("Nikon F2SB", 1976),
            new CameraDef("Nikon F2A", 1977),
            new CameraDef("Nikon F2AS", 1977),
            new CameraDef("Nikon F3", 1980),
            new CameraDef("Nikon F3HP", 1982),
            new CameraDef("Nikon F3T", 1982),
            new CameraDef("Nikon F3AF", 1983),
            new CameraDef("Nikon F3P", 1983),
            new CameraDef("Nikon F4", 1988),
            new CameraDef("Nikon F4S", 1988),
            new CameraDef("Nikon F4E", 1988),
            new CameraDef("Nikon F5", 1996),
            new CameraDef("Nikon F6", 2004),
            // FM series
            new CameraDef("Nikon FM", 1977),
            new CameraDef("Nikon FM2", 1982),
            new CameraDef("Nikon FM2n", 1984),
            new CameraDef("Nikon FM3A", 2001),
            new CameraDef("Nikon FM10", 1995),
            // FE series
            new CameraDef("Nikon FE", 1978),
            new CameraDef("Nikon FE2", 1983),
            new CameraDef("Nikon FE10", 1996),
            // Other manual/semi-auto
            new CameraDef("Nikon FA", 1983),
            new CameraDef("Nikon FG", 1982),
            new CameraDef("Nikon FG-20", 1984),
            new CameraDef("Nikon EM", 1979),
            new CameraDef("Nikon EL2", 1977),
            // Consumer F/N series
            new CameraDef("Nikon F-501", 1986),
            new CameraDef("Nikon F-401", 1987),
            new CameraDef("Nikon F-401s", 1989),
            new CameraDef("Nikon F-601", 1990),
            new CameraDef("Nikon F-801", 1988),
            new CameraDef("Nikon F-801s", 1991),
            new CameraDef("Nikon F50", 1994),
            new CameraDef("Nikon F55", 2002),
            new CameraDef("Nikon F60", 1998),
            new CameraDef("Nikon F65", 2001),
            new CameraDef("Nikon F70", 1994),
            new CameraDef("Nikon F75", 2003),
            new CameraDef("Nikon F80", 2000),
            new CameraDef("Nikon F90", 1992),
            new CameraDef("Nikon F90X", 1994),
            new CameraDef("Nikon F100", 1999),
            // Nikkormat series
            new CameraDef("Nikon Nikkormat FT", 1965),
            new CameraDef("Nikon Nikkormat FTn", 1967),
            new CameraDef("Nikon Nikkormat FT2", 1975),
            new CameraDef("Nikon Nikkormat FT3", 1977),
            new CameraDef("Nikon Nikkormat FS", 1965),
            new CameraDef("Nikon Nikkormat EL", 1972),
            new CameraDef("Nikon Nikkormat ELW", 1976),
            new CameraDef("Nikon Nikkormat EL2", 1977),
            // Pronea (APS film)
            new CameraDef("Nikon Pronea 600i", 1996),
            new CameraDef("Nikon Pronea S", 1999)
    );

    // ---- Coolpix early numeric models (38 models) ----
    private static final List<CameraDef> NIKON_COOLPIX_EARLY = List.of(
            new CameraDef("Nikon Coolpix 100", 1996),
            new CameraDef("Nikon Coolpix 300", 1997),
            new CameraDef("Nikon Coolpix 600", 1998),
            new CameraDef("Nikon Coolpix 700", 1998),
            new CameraDef("Nikon Coolpix 800", 1999),
            new CameraDef("Nikon Coolpix 900", 1998),
            new CameraDef("Nikon Coolpix 910", 1999),
            new CameraDef("Nikon Coolpix 950", 1999),
            new CameraDef("Nikon Coolpix 880", 2000),
            new CameraDef("Nikon Coolpix 990", 2000),
            new CameraDef("Nikon Coolpix 775", 2001),
            new CameraDef("Nikon Coolpix 885", 2001),
            new CameraDef("Nikon Coolpix 995", 2001),
            new CameraDef("Nikon Coolpix 2500", 2002),
            new CameraDef("Nikon Coolpix 4500", 2002),
            new CameraDef("Nikon Coolpix 2000", 2003),
            new CameraDef("Nikon Coolpix 2100", 2003),
            new CameraDef("Nikon Coolpix 3100", 2003),
            new CameraDef("Nikon Coolpix 3500", 2003),
            new CameraDef("Nikon Coolpix 4300", 2003),
            new CameraDef("Nikon Coolpix 5000", 2001),
            new CameraDef("Nikon Coolpix 5400", 2003),
            new CameraDef("Nikon Coolpix 5700", 2002),
            new CameraDef("Nikon Coolpix 2200", 2004),
            new CameraDef("Nikon Coolpix 3200", 2004),
            new CameraDef("Nikon Coolpix 3700", 2004),
            new CameraDef("Nikon Coolpix 4100", 2004),
            new CameraDef("Nikon Coolpix 4200", 2004),
            new CameraDef("Nikon Coolpix 4800", 2004),
            new CameraDef("Nikon Coolpix 5200", 2004),
            new CameraDef("Nikon Coolpix 8400", 2004),
            new CameraDef("Nikon Coolpix 8700", 2004),
            new CameraDef("Nikon Coolpix 8800", 2004),
            new CameraDef("Nikon Coolpix 4600", 2005),
            new CameraDef("Nikon Coolpix 5600", 2005),
            new CameraDef("Nikon Coolpix 5900", 2005),
            new CameraDef("Nikon Coolpix 7600", 2005),
            new CameraDef("Nikon Coolpix 7900", 2005)
    );

    // ---- Coolpix L series (40 models) ----
    private static final List<CameraDef> NIKON_COOLPIX_L = List.of(
            new CameraDef("Nikon Coolpix L1", 2005),
            new CameraDef("Nikon Coolpix L2", 2006),
            new CameraDef("Nikon Coolpix L3", 2006),
            new CameraDef("Nikon Coolpix L4", 2006),
            new CameraDef("Nikon Coolpix L5", 2006),
            new CameraDef("Nikon Coolpix L6", 2006),
            new CameraDef("Nikon Coolpix L10", 2007),
            new CameraDef("Nikon Coolpix L11", 2007),
            new CameraDef("Nikon Coolpix L12", 2007),
            new CameraDef("Nikon Coolpix L14", 2007),
            new CameraDef("Nikon Coolpix L15", 2007),
            new CameraDef("Nikon Coolpix L16", 2008),
            new CameraDef("Nikon Coolpix L18", 2008),
            new CameraDef("Nikon Coolpix L19", 2009),
            new CameraDef("Nikon Coolpix L20", 2009),
            new CameraDef("Nikon Coolpix L21", 2010),
            new CameraDef("Nikon Coolpix L22", 2010),
            new CameraDef("Nikon Coolpix L23", 2011),
            new CameraDef("Nikon Coolpix L24", 2011),
            new CameraDef("Nikon Coolpix L25", 2012),
            new CameraDef("Nikon Coolpix L26", 2012),
            new CameraDef("Nikon Coolpix L27", 2013),
            new CameraDef("Nikon Coolpix L28", 2013),
            new CameraDef("Nikon Coolpix L29", 2014),
            new CameraDef("Nikon Coolpix L30", 2014),
            new CameraDef("Nikon Coolpix L31", 2015),
            new CameraDef("Nikon Coolpix L32", 2015),
            new CameraDef("Nikon Coolpix L100", 2009),
            new CameraDef("Nikon Coolpix L105", 2010),
            new CameraDef("Nikon Coolpix L110", 2010),
            new CameraDef("Nikon Coolpix L120", 2011),
            new CameraDef("Nikon Coolpix L310", 2012),
            new CameraDef("Nikon Coolpix L320", 2013),
            new CameraDef("Nikon Coolpix L330", 2014),
            new CameraDef("Nikon Coolpix L340", 2015),
            new CameraDef("Nikon Coolpix L610", 2012),
            new CameraDef("Nikon Coolpix L620", 2013),
            new CameraDef("Nikon Coolpix L810", 2012),
            new CameraDef("Nikon Coolpix L820", 2013),
            new CameraDef("Nikon Coolpix L830", 2014)
    );

    // ---- Coolpix L series continued + Coolpix S series (91 models) ----
    private static final List<CameraDef> NIKON_COOLPIX_S = List.of(
            new CameraDef("Nikon Coolpix L840", 2015),
            // S series
            new CameraDef("Nikon Coolpix S1", 2004),
            new CameraDef("Nikon Coolpix S2", 2005),
            new CameraDef("Nikon Coolpix S3", 2005),
            new CameraDef("Nikon Coolpix S4", 2005),
            new CameraDef("Nikon Coolpix S5", 2006),
            new CameraDef("Nikon Coolpix S6", 2006),
            new CameraDef("Nikon Coolpix S7", 2006),
            new CameraDef("Nikon Coolpix S7c", 2006),
            new CameraDef("Nikon Coolpix S8", 2007),
            new CameraDef("Nikon Coolpix S9", 2006),
            new CameraDef("Nikon Coolpix S10", 2006),
            new CameraDef("Nikon Coolpix S50", 2007),
            new CameraDef("Nikon Coolpix S50c", 2007),
            new CameraDef("Nikon Coolpix S51", 2007),
            new CameraDef("Nikon Coolpix S51c", 2007),
            new CameraDef("Nikon Coolpix S52", 2008),
            new CameraDef("Nikon Coolpix S52c", 2008),
            new CameraDef("Nikon Coolpix S60", 2008),
            new CameraDef("Nikon Coolpix S70", 2009),
            new CameraDef("Nikon Coolpix S80", 2010),
            new CameraDef("Nikon Coolpix S100", 2011),
            new CameraDef("Nikon Coolpix S200", 2007),
            new CameraDef("Nikon Coolpix S203", 2009),
            new CameraDef("Nikon Coolpix S210", 2008),
            new CameraDef("Nikon Coolpix S220", 2009),
            new CameraDef("Nikon Coolpix S225", 2010),
            new CameraDef("Nikon Coolpix S230", 2009),
            new CameraDef("Nikon Coolpix S500", 2007),
            new CameraDef("Nikon Coolpix S510", 2007),
            new CameraDef("Nikon Coolpix S520", 2008),
            new CameraDef("Nikon Coolpix S550", 2008),
            new CameraDef("Nikon Coolpix S560", 2008),
            new CameraDef("Nikon Coolpix S570", 2009),
            new CameraDef("Nikon Coolpix S600", 2008),
            new CameraDef("Nikon Coolpix S610", 2008),
            new CameraDef("Nikon Coolpix S610c", 2008),
            new CameraDef("Nikon Coolpix S620", 2009),
            new CameraDef("Nikon Coolpix S630", 2009),
            new CameraDef("Nikon Coolpix S640", 2009),
            new CameraDef("Nikon Coolpix S700", 2007),
            new CameraDef("Nikon Coolpix S710", 2008),
            new CameraDef("Nikon Coolpix S800c", 2012),
            new CameraDef("Nikon Coolpix S1000pj", 2009),
            new CameraDef("Nikon Coolpix S1100pj", 2010),
            new CameraDef("Nikon Coolpix S1200pj", 2011),
            new CameraDef("Nikon Coolpix S2500", 2011),
            new CameraDef("Nikon Coolpix S2550", 2010),
            new CameraDef("Nikon Coolpix S2600", 2012),
            new CameraDef("Nikon Coolpix S2700", 2013),
            new CameraDef("Nikon Coolpix S2800", 2014),
            new CameraDef("Nikon Coolpix S2900", 2015),
            new CameraDef("Nikon Coolpix S3000", 2010),
            new CameraDef("Nikon Coolpix S3100", 2011),
            new CameraDef("Nikon Coolpix S3200", 2012),
            new CameraDef("Nikon Coolpix S3300", 2012),
            new CameraDef("Nikon Coolpix S3400", 2013),
            new CameraDef("Nikon Coolpix S3500", 2013),
            new CameraDef("Nikon Coolpix S3600", 2014),
            new CameraDef("Nikon Coolpix S3700", 2015),
            new CameraDef("Nikon Coolpix S4000", 2010),
            new CameraDef("Nikon Coolpix S4100", 2011),
            new CameraDef("Nikon Coolpix S4150", 2011),
            new CameraDef("Nikon Coolpix S4200", 2012),
            new CameraDef("Nikon Coolpix S4300", 2012),
            new CameraDef("Nikon Coolpix S4400", 2013),
            new CameraDef("Nikon Coolpix S5100", 2010),
            new CameraDef("Nikon Coolpix S5200", 2013),
            new CameraDef("Nikon Coolpix S5300", 2014),
            new CameraDef("Nikon Coolpix S6000", 2010),
            new CameraDef("Nikon Coolpix S6100", 2011),
            new CameraDef("Nikon Coolpix S6150", 2011),
            new CameraDef("Nikon Coolpix S6200", 2011),
            new CameraDef("Nikon Coolpix S6300", 2012),
            new CameraDef("Nikon Coolpix S6400", 2012),
            new CameraDef("Nikon Coolpix S6500", 2013),
            new CameraDef("Nikon Coolpix S6600", 2013),
            new CameraDef("Nikon Coolpix S6700", 2013),
            new CameraDef("Nikon Coolpix S6800", 2014),
            new CameraDef("Nikon Coolpix S6900", 2014),
            new CameraDef("Nikon Coolpix S7000", 2015),
            new CameraDef("Nikon Coolpix S8000", 2010),
            new CameraDef("Nikon Coolpix S8100", 2010),
            new CameraDef("Nikon Coolpix S8200", 2011),
            new CameraDef("Nikon Coolpix S9050", 2010),
            new CameraDef("Nikon Coolpix S9100", 2011),
            new CameraDef("Nikon Coolpix S9200", 2012),
            new CameraDef("Nikon Coolpix S9300", 2012),
            new CameraDef("Nikon Coolpix S9400", 2013),
            new CameraDef("Nikon Coolpix S9500", 2013),
            new CameraDef("Nikon Coolpix S9600", 2014),
            new CameraDef("Nikon Coolpix S9700", 2014),
            new CameraDef("Nikon Coolpix S9900", 2015)
    );

    // ---- Coolpix P series (29 models) ----
    private static final List<CameraDef> NIKON_COOLPIX_P = List.of(
            new CameraDef("Nikon Coolpix P1", 2005),
            new CameraDef("Nikon Coolpix P2", 2005),
            new CameraDef("Nikon Coolpix P3", 2006),
            new CameraDef("Nikon Coolpix P4", 2006),
            new CameraDef("Nikon Coolpix P50", 2007),
            new CameraDef("Nikon Coolpix P60", 2008),
            new CameraDef("Nikon Coolpix P80", 2008),
            new CameraDef("Nikon Coolpix P90", 2009),
            new CameraDef("Nikon Coolpix P100", 2010),
            new CameraDef("Nikon Coolpix P300", 2011),
            new CameraDef("Nikon Coolpix P310", 2012),
            new CameraDef("Nikon Coolpix P330", 2013),
            new CameraDef("Nikon Coolpix P340", 2014),
            new CameraDef("Nikon Coolpix P500", 2011),
            new CameraDef("Nikon Coolpix P510", 2012),
            new CameraDef("Nikon Coolpix P520", 2013),
            new CameraDef("Nikon Coolpix P530", 2014),
            new CameraDef("Nikon Coolpix P600", 2014),
            new CameraDef("Nikon Coolpix P610", 2015),
            new CameraDef("Nikon Coolpix P900", 2015),
            new CameraDef("Nikon Coolpix P950", 2020),
            new CameraDef("Nikon Coolpix P1000", 2018),
            new CameraDef("Nikon Coolpix P5000", 2007),
            new CameraDef("Nikon Coolpix P5100", 2007),
            new CameraDef("Nikon Coolpix P6000", 2008),
            new CameraDef("Nikon Coolpix P7000", 2010),
            new CameraDef("Nikon Coolpix P7100", 2011),
            new CameraDef("Nikon Coolpix P7700", 2012),
            new CameraDef("Nikon Coolpix P7800", 2013)
    );

    // ---- Coolpix A, B, W series (16 models) ----
    private static final List<CameraDef> NIKON_COOLPIX_ABW = List.of(
            new CameraDef("Nikon Coolpix A", 2013),
            new CameraDef("Nikon Coolpix A10", 2016),
            new CameraDef("Nikon Coolpix A100", 2016),
            new CameraDef("Nikon Coolpix A300", 2016),
            new CameraDef("Nikon Coolpix A900", 2016),
            new CameraDef("Nikon Coolpix A1000", 2019),
            new CameraDef("Nikon Coolpix AW100", 2011),
            new CameraDef("Nikon Coolpix AW110", 2013),
            new CameraDef("Nikon Coolpix AW120", 2014),
            new CameraDef("Nikon Coolpix AW130", 2015),
            new CameraDef("Nikon Coolpix B500", 2016),
            new CameraDef("Nikon Coolpix B600", 2019),
            new CameraDef("Nikon Coolpix B700", 2016),
            new CameraDef("Nikon Coolpix W100", 2016),
            new CameraDef("Nikon Coolpix W150", 2019),
            new CameraDef("Nikon Coolpix W300", 2017)
    );

    /** All Nikon cameras combined. */
    private static final List<CameraDef> ALL_NIKON_CAMERAS;
    static {
        List<CameraDef> all = new ArrayList<>();
        all.addAll(NIKON_DSLR);
        all.addAll(NIKON_MIRRORLESS);
        all.addAll(NIKON_FILM);
        all.addAll(NIKON_COOLPIX_EARLY);
        all.addAll(NIKON_COOLPIX_L);
        all.addAll(NIKON_COOLPIX_S);
        all.addAll(NIKON_COOLPIX_P);
        all.addAll(NIKON_COOLPIX_ABW);
        ALL_NIKON_CAMERAS = List.copyOf(all);
    }

    private CameraProducer findOrCreateNikon() {
        return producerRepo.findByName("Nikon").orElseGet(() -> {
            CameraProducer p = new CameraProducer();
            p.setName("Nikon");
            p.setFoundationYear(YearMonth.of(1917, 7));
            return producerRepo.save(p);
        });
    }

    private Camera insertIfAbsent(CameraDef def, CameraProducer producer) {
        return cameraRepo.findByName(def.name()).orElseGet(() -> {
            Camera c = new Camera();
            c.setName(def.name());
            c.setReleaseYear(YearMonth.of(def.releaseYear(), 1));
            c.setProducer(producer);
            return cameraRepo.save(c);
        });
    }

    @Test
    void insertsAllNikonCameras() {
        CameraProducer nikon = findOrCreateNikon();

        List<Camera> result = new ArrayList<>();
        for (CameraDef def : ALL_NIKON_CAMERAS) {
            result.add(insertIfAbsent(def, nikon));
        }

        assertEquals(ALL_NIKON_CAMERAS.size(), result.size());
        for (Camera c : result) {
            assertNotNull(c.getId());
            assertEquals("Nikon", c.getProducer().getName());
        }

        // verify all are present in the database
        for (CameraDef def : ALL_NIKON_CAMERAS) {
            assertTrue(cameraRepo.findByName(def.name()).isPresent(),
                    def.name() + " must exist in the database");
        }
    }

    @Test
    void secondRunCreatesNoDuplicates() {
        CameraProducer nikon = findOrCreateNikon();

        // first run
        for (CameraDef def : ALL_NIKON_CAMERAS) {
            insertIfAbsent(def, nikon);
        }
        long countAfterFirstRun = cameraRepo.count();

        // second run — same cameras again
        for (CameraDef def : ALL_NIKON_CAMERAS) {
            insertIfAbsent(def, nikon);
        }
        long countAfterSecondRun = cameraRepo.count();

        assertEquals(countAfterFirstRun, countAfterSecondRun,
                "Second run must not create duplicates");
    }

    @Test
    void eachCameraHasCorrectReleaseYear() {
        CameraProducer nikon = findOrCreateNikon();
        for (CameraDef def : ALL_NIKON_CAMERAS) {
            insertIfAbsent(def, nikon);
        }

        for (CameraDef def : ALL_NIKON_CAMERAS) {
            Camera found = cameraRepo.findByName(def.name()).orElseThrow(
                    () -> new AssertionError(def.name() + " not found"));
            assertEquals(YearMonth.of(def.releaseYear(), 1), found.getReleaseYear(),
                    "Release year mismatch for " + def.name());
        }
    }
}
