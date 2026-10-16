export interface Env {
	DB: D1Database;
}

const CORS_HEADERS: Record<string, string> = {
	"Access-Control-Allow-Origin": "*",
	"Access-Control-Allow-Methods": "GET, POST, OPTIONS",
	"Access-Control-Allow-Headers": "Content-Type, Authorization",
};

function jsonResponse(data: unknown, status = 200): Response {
	return new Response(JSON.stringify(data), {
		status,
		headers: {
			"Content-Type": "application/json",
			...CORS_HEADERS,
		},
	});
}

function errorResponse(error: string, status: number): Response {
	return jsonResponse({ error }, status);
}

function extractBearerToken(request: Request): string | null {
	const auth = request.headers.get("Authorization");
	if (!auth) {
		return null;
	}
	const match = auth.match(/^Bearer\s+(.+)$/i);
	return match ? match[1].trim() : null;
}

export default {
	async fetch(request: Request, env: Env): Promise<Response> {
		if (request.method === "OPTIONS") {
			return new Response(null, {
				status: 204,
				headers: CORS_HEADERS,
			});
		}

		const url = new URL(request.url);
		const path = url.pathname;

		if (path === "/sync" && request.method === "POST") {
			const uuid = extractBearerToken(request);
			if (!uuid) {
				return errorResponse("Unauthorized: Missing or invalid Bearer token", 401);
			}

			let payload: unknown;
			try {
				payload = await request.json();
			} catch {
				return errorResponse("Invalid JSON payload", 400);
			}

			const dataJson = typeof payload === "string" ? payload : JSON.stringify(payload);
			const updatedAt = Math.floor(Date.now() / 1000);

			try {
				await env.DB.prepare(
					`INSERT INTO player_states (uuid, data, updated_at)
					 VALUES (?, ?, ?)
					 ON CONFLICT(uuid) DO UPDATE SET data = excluded.data, updated_at = excluded.updated_at`
				)
					.bind(uuid, dataJson, updatedAt)
					.run();

				return jsonResponse({ success: true });
			} catch (err: unknown) {
				const message = err instanceof Error ? err.message : "Database error";
				return errorResponse(message, 500);
			}
		}

		if (path === "/state" && request.method === "GET") {
			const uuid = extractBearerToken(request);
			if (!uuid) {
				return errorResponse("Unauthorized: Missing or invalid Bearer token", 401);
			}

			try {
				const row = await env.DB.prepare(
					"SELECT data, updated_at FROM player_states WHERE uuid = ?"
				)
					.bind(uuid)
					.first<{ data: string; updated_at: number }>();

				if (!row) {
					return errorResponse("Not Found", 404);
				}

				let parsedData: unknown;
				try {
					parsedData = JSON.parse(row.data);
				} catch {
					parsedData = row.data;
				}

				return jsonResponse({
					data: parsedData,
					updated_at: row.updated_at,
				});
			} catch (err: unknown) {
				const message = err instanceof Error ? err.message : "Database error";
				return errorResponse(message, 500);
			}
		}

		return errorResponse("Not Found", 404);
	},
};
