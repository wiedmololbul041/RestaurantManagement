
public class PMO_Verdict {
	public static void show(boolean result) {
		PMO_SystemOutRedirect.println("--------------------------------------------------------");
		if (result) {
			PMO_SystemOutRedirect.println("   ___  _  __");
			PMO_SystemOutRedirect.println("  / _ \\| |/ /");
			PMO_SystemOutRedirect.println(" | | | | ' / ");
			PMO_SystemOutRedirect.println(" | |_| | . \\ ");
			PMO_SystemOutRedirect.println("  \\___/|_|\\_\\");

			PMO_SystemOutRedirect.println("");
			PMO_SystemOutRedirect.println("--- NIE WYKRYTO BLEDU (co nie oznacza, ze go nie ma) ---");
		} else {
			PMO_SystemOutRedirect.println("BLAD");
			PMO_SystemOutRedirect.println("  ____  _        _    ____  ");
			PMO_SystemOutRedirect.println(" | __ )| |      / \\  |  _ \\ ");
			PMO_SystemOutRedirect.println(" |  _ \\| |     / _ \\ | | | |");
			PMO_SystemOutRedirect.println(" | |_) | |___ / ___ \\| |_| |");
			PMO_SystemOutRedirect.println(" |____/|_____/_/   \\_\\____/ ");
		}
		PMO_SystemOutRedirect.println("--------------------------------------------------------");
	}

}
