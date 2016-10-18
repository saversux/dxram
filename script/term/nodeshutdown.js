function help() {
	return "Shutdown a DXRAM node\n" +
			"Parameters: nid kill\n" +
			"  nid: Id of the node to shutdown\n" +
			"  kill: If specified, true for a hard shutdown (kill process), false for proper soft shutdown (default)"
}

function exec(nid, kill) {
	var boot = dxram.service("boot");

	if (nid == null) {
		dxterm.printlnErr("No nodeID specified");
		return;
	}

	if (kill == null) {
		kill = false;
	}

	if (!boot.shutdownNode(nid, kill)) {
		dxterm.println("Shutting down node " + dxram.shortToHexStr(nid) + " failed");
	} else {
		dxterm.println("Shutting down node " + dxram.shortToHexStr(nid) + "...");
	}
}