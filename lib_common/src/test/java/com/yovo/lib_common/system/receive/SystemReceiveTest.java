package com.yovo.lib_common.system.receive;

import com.yovo.lib_common.system.receive.model.IncomingOpen;
import com.yovo.lib_common.system.receive.model.IncomingShare;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class SystemReceiveTest {

    @Test
    public void readShare_parsesAndDeduplicatesStreams() {
        Uri a = Uri.parse("content://sender/a.bin");
        Uri b = Uri.parse("content://sender/b.bin");
        ArrayList<Uri> streams = new ArrayList<>();
        streams.add(a);
        streams.add(b);

        Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
        intent.setType("application/octet-stream");
        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, streams);
        ClipData clip = ClipData.newRawUri("shared", a);
        clip.addItem(new ClipData.Item(b));
        intent.setClipData(clip);

        IncomingShare incoming = YoReceive.readShare(intent);

        assertEquals(2, incoming.streamUris().size());
        assertEquals("application/octet-stream", incoming.declaredMimeType());
        assertTrue(incoming.isMultiple());
    }

    @Test
    public void readShare_rejectsNonShareAction() {
        assertNull(YoReceive.readShare(new Intent(Intent.ACTION_VIEW)));
    }

    @Test
    public void readOpen_preservesProtocolModeAndGrantFlags() {
        Uri uri = Uri.parse("content://sender/fw.ota");
        Intent intent = new Intent(Intent.ACTION_EDIT);
        intent.setDataAndType(uri, "application/octet-stream");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        IncomingOpen incoming = YoReceive.readOpen(intent);

        assertEquals(IncomingOpen.Mode.EDIT, incoming.mode());
        assertEquals(uri, incoming.uri());
        assertTrue((incoming.grantFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
        assertTrue((incoming.grantFlags() & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0);
    }

    @Test
    public void readOpen_requiresDataUri() {
        assertNull(YoReceive.readOpen(new Intent(Intent.ACTION_VIEW)));
    }

    @Test
    public void emptySharePayload_isRepresentedWithoutInventingFiles() {
        IncomingShare incoming = YoReceive.readShare(new Intent(Intent.ACTION_SEND));
        assertFalse(incoming.hasContent());
    }
}
