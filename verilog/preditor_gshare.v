
module predictor_gshare
   (
    parameter gshare_tam = 10,
    parameter operadores_gshare = 32
    )
   (
    input clk,
    input rst,

  
    output resultado_gshare,     //resultado do preditor

    input desviou_gshare,       // desviou
    input nao_desviou_gshare,      // não desviou
    input desvia,               // desvia
    input nao_desvia,              // não desvia
    input decode_gshare,         
    input previ,                // previzão anterior

    // resolver o branch 
    input hit_gshare,      // hit
    input miss_gshare,    //  miss
    
    input [operadores_gshare-1:0]  brn_pc_i
    );

   localparam [1:0]
      fotemente_nao_tomado = 2'b00,
      nao_tomado           = 2'b01,
      tomado               = 2'b10,
      fortemente_tomado    = 2'b11;
   localparam fsm = 2 ** gshare_tam;
   
   integer i = 0;

   reg [1:0] estado [0:fsm];
   reg [gshare_tam:0] hist_nao_desvia_gshare = 0;
   
   reg [gshare_tam - 1:0] prev_index = 0;
   
   // 
   wire [gshare_tam - 1:0] estado_index = hist_nao_desvia_gshare[gshare_tam - 1:0] ^ brn_pc_i[gshare_tam + 1:2];

   assign resultado_gshare = (estado[estado_index][1] && desvia) ||
                             (!estado[estado_index][1] && nao_desvia);
   wire desvio_tomado = (desviou_gshare && previ) || (nao_desviou_gshare && !previ);
   
   always @(posedge clk) begin
      if (rst) begin
         hist_nao_desvia_gshare <= 0;
         prev_index <= 0;
         for(i = 0; i < fsm; i = i + 1) begin //inicialmente o desvio eh suposto tomado
            estado[i] = tomado;
         end
      end else begin
         if (desvia || nao_desvia) begin
            // armazena o estado em prev index
            prev_index <= estado_index;
         end
         
         if (hit_gshare && decode_gshare) begin
            hist_nao_desvia_gshare <= {hist_nao_desvia_gshare[gshare_tam - 1 : 0], desvio_tomado};
            if (!desvio_tomado) begin
               // muda o  estado da fsm:
               //   fortemente_tomado-> estado_tomado
               //   tomado-> nao_tomado
               //   nao_tomado -> fotemente_nao_tomado
               //   fotemente_nao_tomado -> fotemente_nao_tomado
               case (estado[prev_index])
                 fortemente_tomado:
                     estado[prev_index] <= tomado;
                 tomado:
                     estado[prev_index] <= nao_tomado;
                  nao_tomado:
                     estado[prev_index] <= fotemente_nao_tomado;
                  fotemente_nao_tomado:
                     estado[prev_index] <= fotemente_nao_tomado;
               endcase
            end else begin
               // muda o estado da fsm:
               //   fotemente_nao_tomado -> nao_tomado
               //   nao_tomado -> estado_tomado
               //   tomado-> estado_fortemente_tomado
               //   fortemente_tomado-> estado_fortemente_tomado
               case (estado[prev_index])
                  fotemente_nao_tomado:
                     estado[prev_index] <= nao_tomado;
                  nao_tomado:
                     estado[prev_index] <= tomado;
                  tomado:
                     estado[prev_index] <= fortemente_tomado;
                  fortemente_tomado:
                     estado[prev_index] <= fortemente_tomado;
               endcase
            end
         end
      end
   end
endmodule
