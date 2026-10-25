package jobtracker.ia;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint TEMPORÁRIO para testar o classificador manualmente. Só existe no perfil "dev"
 * (SPRING_PROFILES_ACTIVE=dev) e não grava nada no banco. Requer JWT, como as demais rotas.
 */
@Profile("dev")
@RestController
@RequestMapping("/api/dev")
public class DevClassificacaoController {

	private final ClassificadorEmailService classificador;

	public DevClassificacaoController(ClassificadorEmailService classificador) {
		this.classificador = classificador;
	}

	public record ClassificarRequest(String remetente, String assunto, @NotBlank String corpo) {}

	@PostMapping("/classificar")
	public ClassificacaoEmail classificar(@Valid @RequestBody ClassificarRequest request) {
		return classificador.classificar(request.remetente(), request.assunto(), request.corpo());
	}
}
