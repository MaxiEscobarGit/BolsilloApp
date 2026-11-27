import { File, Paths } from 'expo-file-system';
import * as Sharing from 'expo-sharing';
import { Platform } from 'react-native';

// Saves a text file and opens the share sheet (Files, Mail, WhatsApp, Excel…); on web, downloads it.
export async function shareTextFile(name: string, text: string, mimeType: string) {
  if (Platform.OS === 'web') {
    const url = URL.createObjectURL(new Blob([text], { type: mimeType }));
    const a = Object.assign(document.createElement('a'), { href: url, download: name });
    a.click();
    URL.revokeObjectURL(url);
    return;
  }
  const file = new File(Paths.cache, name);
  if (file.exists) file.delete();
  await file.write(text);
  try {
    await Sharing.shareAsync(file.uri, { mimeType, UTI: 'public.comma-separated-values-text', dialogTitle: name });
  } finally {
    // The export holds the user's movements: don't leave it in the cache once shared.
    if (file.exists) file.delete();
  }
}
