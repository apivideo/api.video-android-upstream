package video.api.upstream.models.storage

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import video.api.upstream.models.storage.FileUpstreamDao.Companion.writeToDisk

class FileUpstreamDaoTest {
    @get:Rule
    val rootFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun `test get session by id`() {
        val workDir = rootFolder.newFolder()
        val dao = FileUpstreamDao(workDir)
        dao.insert("MockId1")

        UpstreamSessionEntity("MockId2", "VIDEO_ID_1", null, emptyList()).writeToDisk(workDir)
        UpstreamSessionEntity("MockId3", null, "UPLOAD_TOKEN_1", emptyList()).writeToDisk(workDir)

        assertNotNull(dao.getById("MockId1"))
        assertNotNull(dao.getById("MockId2"))
        assertNull(dao.getById("UnknownMockId"))
    }

    @Test
    fun `test get session by video id`() {
        val workDir = rootFolder.newFolder()
        val dao = FileUpstreamDao(workDir)

        UpstreamSessionEntity("MockId1", "VIDEO_ID_1", null, emptyList()).writeToDisk(workDir)
        UpstreamSessionEntity("MockId2", null, "UPLOAD_TOKEN_1", emptyList()).writeToDisk(workDir)

        assertNotNull(dao.getByVideoId("VIDEO_ID_1"))
        assertNull(dao.getByVideoId("UNKNOWN_VIDEO_ID"))
    }

    @Test
    fun `test get session by upload token`() {
        val workDir = rootFolder.newFolder()
        val dao = FileUpstreamDao(workDir)

        UpstreamSessionEntity("MockId1", "VIDEO_ID_1", null, emptyList()).writeToDisk(workDir)
        UpstreamSessionEntity("MockId2", null, "UPLOAD_TOKEN_1", emptyList()).writeToDisk(workDir)
        UpstreamSessionEntity("MockId3", "VIDEO_ID_2", "UPLOAD_TOKEN_1", emptyList()).writeToDisk(
            workDir
        )

        assertEquals(2, dao.getByToken("UPLOAD_TOKEN_1", null).size)
        assertEquals(1, dao.getByToken("UPLOAD_TOKEN_1", "VIDEO_ID_2").size)
        assertEquals(0, dao.getByToken("UNKNOWN_UPLOAD_TOKEN", null).size)
        assertEquals(0, dao.getByToken("UPLOAD_TOKEN_1", "UNKNOWN_VIDEO_ID").size)
    }

    @Test
    fun `test list sessions`() {
        val workDir = rootFolder.newFolder()
        val dao = FileUpstreamDao(workDir)

        UpstreamSessionEntity("MockId1", "VIDEO_ID_1", null, emptyList()).writeToDisk(workDir)
        UpstreamSessionEntity("MockId2", null, "UPLOAD_TOKEN_1", emptyList()).writeToDisk(workDir)

        assertEquals(2, dao.allSessions.size)
    }

    @Test
    fun `remove session`() {
        val workDir = rootFolder.newFolder()
        val dao = FileUpstreamDao(workDir)

        dao.insert("MockId1")
        dao.insert("MockId2")
        dao.remove("MockId1")

        assertEquals(1, dao.allSessions.size)
    }

    @Test
    fun `test insert video id`() {
        val workDir = rootFolder.newFolder()
        val dao = FileUpstreamDao(workDir)

        UpstreamSessionEntity("MockId1", null, "UPLOAD_TOKEN_1", emptyList()).writeToDisk(workDir)
        dao.insertVideoId("MockId1", "VIDEO_ID_1")
        dao.insertVideoId("MockId1", "VIDEO_ID_2") // Only first is written

        assertNotNull(dao.getByVideoId("VIDEO_ID_1"))
    }

    @Test
    fun `test insert video id on non existing session`() {
        val workDir = rootFolder.newFolder()
        val dao = FileUpstreamDao(workDir)

        try {
            dao.insertVideoId("MockId1", "VIDEO_ID_1")
            fail("Should throw an exception")
        } catch (e: Exception) {
            // Expected
        }
    }

    @Test
    fun `test insert token`() {
        val workDir = rootFolder.newFolder()
        val dao = FileUpstreamDao(workDir)

        dao.insert("MockId1")
        dao.insertToken("MockId1", "UPLOAD_TOKEN_1")

        assertEquals(1, dao.getByToken("UPLOAD_TOKEN_1", null).size)
    }

    @Test
    fun `test insert a part`() {
        val workDir = rootFolder.newFolder()
        val dao = FileUpstreamDao(workDir)

        dao.insert("MockId1")
        assertFalse(dao.hasParts("MockId1"))

        dao.insertPart("MockId1", Part(1, false, rootFolder.newFile("1")))
        dao.insertPart("MockId1", Part(2, false, rootFolder.newFile("2")))
        dao.insertPart("MockId1", Part(3, false, rootFolder.newFile("3")))

        val parts = dao.getParts("MockId1")
        assertEquals(3, parts.size)
        assertTrue(dao.hasParts("MockId1"))
    }


    @Test
    fun `test insert a part for non existing session`() {
        val workDir = rootFolder.newFolder()
        val dao = FileUpstreamDao(workDir)

        try {
            dao.insertPart("MockId1", Part(1, false, rootFolder.newFile("1")))
            fail("Should throw an exception")
        } catch (e: Exception) {
            // Expected
        }
    }

    @Test
    fun `test insert part with same index`() {
        val workDir = rootFolder.newFolder()
        val dao = FileUpstreamDao(workDir)

        try {
            dao.insert("MockId1")
            dao.insertPart("MockId1", Part(1, false, rootFolder.newFile("1")))
            dao.insertPart("MockId1", Part(1, false, rootFolder.newFile("2")))
            fail("Should throw an exception")
        } catch (e: Exception) {
            // Expected
        }
    }

    @Test
    fun `test remove a part`() {
        val workDir = rootFolder.newFolder()
        val dao = FileUpstreamDao(workDir)

        dao.insert("MockId1")
        dao.insertPart("MockId1", Part(1, false, rootFolder.newFile("1")))
        dao.insertPart("MockId1", Part(2, false, rootFolder.newFile("2")))
        dao.insertPart("MockId1", Part(3, false, rootFolder.newFile("3")))

        dao.removePart("MockId1", 2)
        assertEquals(2, dao.getParts("MockId1").size)
        dao.removePart("MockId1", 1)
        dao.removePart("MockId1", 3)
        assertEquals(0, dao.getParts("MockId1").size)
        assertFalse(dao.hasParts("MockId1"))
    }

    @Test
    fun `test get last part id`() {
        val workDir = rootFolder.newFolder()
        val dao = FileUpstreamDao(workDir)

        dao.insert("MockId1")
        assertNull(dao.getLastPartId("MockId1"))

        dao.insertPart("MockId1", Part(1, true, rootFolder.newFile("1")))
        assertEquals(1, dao.getLastPartId("MockId1"))
    }

    @Test
    fun `test get last part id on non existing session`() {
        val workDir = rootFolder.newFolder()
        val dao = FileUpstreamDao(workDir)

        assertNull(dao.getLastPartId("MockId1"))
    }
}