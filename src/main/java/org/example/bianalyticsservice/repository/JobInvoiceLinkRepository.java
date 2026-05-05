package org.example.bianalyticsservice.repository;

import org.example.bianalyticsservice.model.CtiZlecenieNag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobInvoiceLinkRepository extends JpaRepository<CtiZlecenieNag, Integer> {

    /**
     * Returns one row per (production_order, FS_invoice) pair.
     * Columns: czn_id, ro_id, ro_no, fs_id, fs_no, fs_date, co_zp_count, fs_lines_json,
     *          fs_korr_net, job_product_code_full
     *
     * fs_lines_json is a SQL Server FOR JSON PATH array of {twrKod, ilosc, wartoscNetto}
     * for every line on the FS, parsed by the service into List<InvoiceLineDto>.
     *
     * job_product_code_full comes from CDN.Towary.Twr_Kod via the ZP's CZN_TwrId — the full,
     * non-truncated product code used for exact Tier-1 matching against FS lines. (CZN_TwrKod
     * on the ZP itself is varchar(20) and silently truncates longer codes.)
     *
     * Filters:
     *   - FS must be active (TrN_Anulowany = 0) and prefixed FS/
     *   - chain: ZP -> CtiZlecenieDok -> RW/PW (TraNag) -> TraNagRelacje (304/303 -> 308) -> RO
     *     and from RO either:
     *       - direct RO -> FS                    (308 -> 302), or
     *       - RO -> WZ -> FS                     (308 -> 306, 306 -> 302)
     *     The WZ path matters: many FSs are generated from a delivery note rather than directly
     *     from the customer order, and without this path those FSs are invisible to analytics.
     */
    @Query(value = """
            WITH ro_for_job AS (
                -- Path 1: ZP → CtiZlecenieDok (RW/PW) → TraNagRelacje → RO.
                -- Used by product-manufacturing ZPs that move material through the warehouse.
                SELECT DISTINCT czd.CZD_CZNId AS czn_id, rel.TrR_FaId AS ro_id
                FROM dbo.CtiZlecenieDok czd
                JOIN CDN.TraNagRelacje rel
                  ON rel.TrR_TrNId = czd.CZD_TrnId
                 AND rel.TrR_TrNTyp IN (303, 304)
                 AND rel.TrR_FaTyp = 308
                UNION
                -- Path 2: ZP.CZN_DokZwiazID → RO directly. Service-type jobs (NAPRAWA,
                -- USŁUGA, MONTAŻ, etc.) bypass the warehouse-doc chain entirely; they store
                -- the source RO in the ZP header instead of going through CtiZlecenieDok.
                SELECT DISTINCT czn.CZN_ID AS czn_id, czn.CZN_DokZwiazID AS ro_id
                FROM dbo.CtiZlecenieNag czn
                JOIN CDN.TraNag ro ON ro.TrN_TrNID = czn.CZN_DokZwiazID
                                  AND ro.TrN_TypDokumentu = 308
                WHERE czn.CZN_DokZwiazID IS NOT NULL
                  AND czn.CZN_DokZwiazTyp = 308
            ),
            ro_info AS (
                SELECT DISTINCT r.ro_id, ro.TrN_NumerPelny AS ro_no
                FROM ro_for_job r
                JOIN CDN.TraNag ro ON ro.TrN_TrNID = r.ro_id AND ro.TrN_TypDokumentu = 308
            ),
            fs_for_ro AS (
                -- direct RO -> FS
                SELECT DISTINCT r.ro_id,
                                fs.TrN_TrNID AS fs_id,
                                fs.TrN_NumerPelny AS fs_no,
                                CAST(fs.TrN_DataDok AS date) AS fs_date
                FROM ro_for_job r
                JOIN CDN.TraNagRelacje rel ON rel.TrR_TrNId = r.ro_id
                                          AND rel.TrR_TrNTyp = 308
                                          AND rel.TrR_FaTyp = 302
                JOIN CDN.TraNag fs ON fs.TrN_TrNID = rel.TrR_FaId
                                  AND fs.TrN_Anulowany = 0
                                  AND fs.TrN_NumerPelny LIKE 'FS/%'
                UNION
                -- RO -> WZ -> FS
                SELECT DISTINCT r.ro_id,
                                fs.TrN_TrNID AS fs_id,
                                fs.TrN_NumerPelny AS fs_no,
                                CAST(fs.TrN_DataDok AS date) AS fs_date
                FROM ro_for_job r
                JOIN CDN.TraNagRelacje r1 ON r1.TrR_TrNId = r.ro_id
                                         AND r1.TrR_TrNTyp = 308
                                         AND r1.TrR_FaTyp  = 306
                JOIN CDN.TraNagRelacje r2 ON r2.TrR_TrNId = r1.TrR_FaId
                                         AND r2.TrR_TrNTyp = 306
                                         AND r2.TrR_FaTyp  = 302
                JOIN CDN.TraNag fs ON fs.TrN_TrNID = r2.TrR_FaId
                                  AND fs.TrN_Anulowany = 0
                                  AND fs.TrN_NumerPelny LIKE 'FS/%'
            ),
            co_zp_in_ro AS (
                SELECT ro_id, COUNT(DISTINCT czn_id) AS co_zp_count
                FROM ro_for_job
                GROUP BY ro_id
            )
            SELECT
                rfj.czn_id    AS czn_id,
                rfj.ro_id     AS ro_id,
                ri.ro_no      AS ro_no,
                fr.fs_id      AS fs_id,
                fr.fs_no      AS fs_no,
                fr.fs_date    AS fs_date,
                c.co_zp_count AS co_zp_count,
                (SELECT CAST(e.TrE_TwrKod AS NVARCHAR(255)) AS twrKod,
                        e.TrE_Ilosc     AS ilosc,
                        e.TrE_WartoscNetto AS wartoscNetto
                 FROM CDN.TraElem e
                 WHERE e.TrE_TrNId = fr.fs_id
                 FOR JSON PATH) AS fs_lines_json,
                ISNULL((SELECT SUM(ke.TrE_WartoscNetto)
                        FROM CDN.TraNagRelacje kr
                        JOIN CDN.TraNag kfs ON kfs.TrN_TrNID = kr.TrR_TrNId
                                            AND kfs.TrN_TypDokumentu = 302
                                            AND kfs.TrN_NumerPelny LIKE 'FSKOR/%'
                                            AND kfs.TrN_Anulowany = 0
                        JOIN CDN.TraElem ke ON ke.TrE_TrNId = kfs.TrN_TrNID
                        WHERE kr.TrR_FaId = fr.fs_id AND kr.TrR_FaTyp = 302), 0) AS fs_korr_net,
                CAST(t.Twr_Kod AS NVARCHAR(255)) AS job_product_code_full
            FROM ro_for_job rfj
            JOIN fs_for_ro fr   ON fr.ro_id = rfj.ro_id
            JOIN co_zp_in_ro c  ON c.ro_id  = rfj.ro_id
            JOIN ro_info ri     ON ri.ro_id = rfj.ro_id
            JOIN dbo.CtiZlecenieNag czn ON czn.CZN_ID = rfj.czn_id
            LEFT JOIN CDN.Towary t      ON t.Twr_TwrId = czn.CZN_TwrId
            """,
            nativeQuery = true)
    List<Object[]> findAllInvoiceLinks();
}
