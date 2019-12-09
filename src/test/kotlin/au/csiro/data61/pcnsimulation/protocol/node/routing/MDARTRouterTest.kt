package au.csiro.data61.pcnsimulation.protocol.node.routing

import au.csiro.data61.pcnsimulation.WalletAddress
import au.csiro.data61.pcnsimulation.protocol.blockchain.Blockchain
import au.csiro.data61.pcnsimulation.protocol.blockchain.GenericBlockchain
import au.csiro.data61.pcnsimulation.protocol.channel.StaticChannelInformation
import au.csiro.data61.pcnsimulation.protocol.channel.TransactionChannel
import au.csiro.data61.pcnsimulation.protocol.channel.transaction.FundingTransaction
import au.csiro.data61.pcnsimulation.protocol.channel.transaction.UnconditionalOutput
import au.csiro.data61.pcnsimulation.protocol.communication.SyncIPNetwork
import au.csiro.data61.pcnsimulation.protocol.node.BasicNode
import au.csiro.data61.pcnsimulation.protocol.node.Node
import au.csiro.data61.pcnsimulation.protocol.strategy.Strategy
import au.csiro.data61.pcnsimulation.template.network.NetworkTemplate
import au.csiro.data61.pcnsimulation.ui.PCNSimulationCLI
import au.csiro.data61.pcnsimulation.util.MockBlockchain
import com.google.gson.GsonBuilder
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.security.KeyPair
import kotlin.math.log2
import kotlin.math.roundToInt

class MDARTRouterTest {

    private lateinit var nodes: Map<WalletAddress, Node>
    private lateinit var channels: List<TransactionChannel>
    private lateinit var communication: SyncIPNetwork
    private lateinit var blockchain: Blockchain

    private val templateJSON =
            """
                {
                    "nodes": [
                      {
                        "name": "ALICE",
                        "keyPair": {
                          "privateKey": "MIICeAIBADANBgkqhkiG9w0BAQEFAASCAmIwggJeAgEAAoGBAKZoNyhITZrNiV8wQLSwaB427BLsuwZVPnluHYAF1JMootc4YlJ6gWFix+Ny7uOxERIN7ZxglhMIAPNH9gQn+k3taxWPiWcrBqB9lhs6YTUE5jMNmIcUiGT1rC387xk9rd7n7c0rhWPy+fPJK8M0F58hwt4mifC8asvI+wiM77p3AgMBAAECgYEAi9zt9yqGJ4V3X40j5XqbmEGbO/DC5PjC0LcPjmnYGHPAIlpesWoL0nl+/hm81Z0edulD+/pmSMqiWdfy291qD7fmG4tfdZjjT6IuQTQ97qSzHbqfiX4nJfBTa831UrJysIErhKyOpow5wdWctNbYgjWyIJHgpFlyTi6pr4FVsIkCQQDajUnPiT1qtfCit3Zc73W0QvZ61Ti+XoLjDSONQIPdlqlryUVkH25k22TXpX1tHkdliPAQka6cCbF//59xR5qtAkEAwuucZ0K5pYEGA2WOL/l9Aqc2gTxfqMxjZbxtL2yycSxWOjoFc9RZ/li3oYFj+89nM4gRV4uimEq4zaHuTTLSMwJBAIujNGK+jBgvMRW15JFSikDne/ZVX5D6b+REE8//RYGB7rOogIaNMoqMRu0llnLuoq10BfaALjESXG+s0qGdH20CQGSa+1UIyY009QeXyTXVzcIq0CUQJkeXhs4h3TEXJ1rmiXLwM5Q/TFvNKqp/gD2eJSQh3OGUvs1f89ae98J8sqUCQQC0aJbFHmlsosKkSVfzUdt9tj7CD4e8wCtJCrenXsauOvm0GVn8BGomggIJfd5yO9wXFp6pPpivJ+XSAF3eKeAn",
                          "publicKey": "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCmaDcoSE2azYlfMEC0sGgeNuwS7LsGVT55bh2ABdSTKKLXOGJSeoFhYsfjcu7jsRESDe2cYJYTCADzR/YEJ/pN7WsVj4lnKwagfZYbOmE1BOYzDZiHFIhk9awt/O8ZPa3e5+3NK4Vj8vnzySvDNBefIcLeJonwvGrLyPsIjO+6dwIDAQAB"
                        },
                        "ipAddress": "10.0.0.1",
                        "walletAddress": "0x045AC964A9",
                        "walletBalance": 18.22699775195737
                      },
                      {
                        "name": "BOB",
                        "keyPair": {
                          "privateKey": "MIICdwIBADANBgkqhkiG9w0BAQEFAASCAmEwggJdAgEAAoGBAJ66vVwCddazLNrB8oF6ULoiq1Jk/5U8+kJ0w1SN8PuITwgV/VE9x+uXCQyncv+UFWjgUzJER4ssHKBJ5L3E93tDkqZcUnpgbs8xu1EqOeGVL0N0k8yxSEd7H47R37O0Etm8+VoWzwbGsqPUH7Pbyozhnk12SkudWrioE9FywohhAgMBAAECgYB65+ncJuOL3a1rriXS8B02I8Y4xI/KxS6a6sKc5FOm0MMxZaWClK4K1CZjr/0xvT5euORy1b/4Ga4Ix27hKnTduYfwHtvAg42RyslCkmeI/9N3XSu6k/dlIVTdw2PDZJiGyjgFkt1XYYBwwVgU9ibgNRDwDQAFqio6ZCGq3NI2iQJBAN8qJmU3jKTiiWzIOxvgYAoP8WKa114LqTpMfI529pTFzyudZ4cq4GNCBMh5AOnUAj+WBRRV+bE2fgcMPQWy6PMCQQC2FYk0BBREjWfmc4xoPHONdfPsFL728tamUH+AK5e4A/dk4XZitU0BtEUULsD/pSsORDA3+1lY6qVIbwg6/d5bAkEAjoz3H7qfHYgTbqhskX7++g57C5iLL+jU/Yd0HPDCy/+bOWn/gqkR0nWOZpxcyACEOyfSMM5GhDuhv1DC/gvtnwJAR/szhjCmooMUM/Ix63Maay3aA9YfuBg/6vefr9eL3t8SifrnhhYOH0JYnKXu/iEZaEZkmXzv0UrRlFTweAGsSQJBAIyB/nYU4Li6yM6vtEXlfEXxMiEhuNYJ41fW++O6/2L/SngNJWnJd4IqMvsV2PTRWPmZw6cVKn4ns1SVGHCIwWg\u003d",
                          "publicKey": "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCeur1cAnXWsyzawfKBelC6IqtSZP+VPPpCdMNUjfD7iE8IFf1RPcfrlwkMp3L/lBVo4FMyREeLLBygSeS9xPd7Q5KmXFJ6YG7PMbtRKjnhlS9DdJPMsUhHex+O0d+ztBLZvPlaFs8GxrKj1B+z28qM4Z5NdkpLnVq4qBPRcsKIYQIDAQAB"
                        },
                        "ipAddress": "10.0.0.2",
                        "walletAddress": "0x1E3D661B65",
                        "walletBalance": 13.945906669195878
                      },
                      {
                        "name": "CAROL",
                        "keyPair": {
                          "privateKey": "MIICdgIBADANBgkqhkiG9w0BAQEFAASCAmAwggJcAgEAAoGBAJc18AHyrRBy9kVueSV60hJnDt9hXJ9DnNt60Jq2lEUMi55D8FVw5wESBgHPOnykBj7M4uVIehUVJ67cpk2Hv5FwzWfXEvyRrnSO2D+mqBI76XtYWMrr95pL+8k3TQrtpvgxZrLQiQ1IqvOVWqDMOhRZ483/q10Ve7bBZn/A2YFbAgMBAAECgYEAjQm6O5bm5ZjVLB3G0balipSlwqVFhyfBftYnDKeP1HfHIm+sE4WjcdQl1jo8C1XSnXDtkX7woAmDYg5c76PWgZb0zw8gU4CNiI5hwx8Pje02qpdrL4T0wuARc4hjdeG3qRjWyaUvbBUzAv5FusLb6f/a5SM4YeknnpADLn0sR8ECQQDkCSn8D3jhCzfj/IuE/x1ZqnUKfSAbA1nAtUIHZJcOvOr0AP+Xt0aCk7imK5vds8n/Lcm9iXJnuvBe6Bhq4KAlAkEAqcD3csDmBNxsOddgGfFnWFw0+4QPZJ6Vn2SZXy+FTevqhzS1JgT/ec6gnKlenjjpHIqkaa1Nm6edce56oeEjfwJAUkzwZzWaVfZ7jIAoRq7gg+0eYtO4E6fI6+E/XHW0gzAxyDDYDoSqIRR5jxesIH70B3IaHpsNvFxexpITxfceNQJACS3+M55q0eh5kccr0ztSs1yJIPDLRE2vGad/A762HribPiSDh0LN7fBWjyI5k6TQNlLpAS31/Gzb/sU+rHJYUQJAc33oMgOxKRTQYIA1MwwfoL2eDzqN4THbYawVa5k2vPT//c9LlGo7p13tRzQyD7q7foT4j0LiEFtglTNnGg7dHg\u003d\u003d",
                          "publicKey": "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCXNfAB8q0QcvZFbnkletISZw7fYVyfQ5zbetCatpRFDIueQ/BVcOcBEgYBzzp8pAY+zOLlSHoVFSeu3KZNh7+RcM1n1xL8ka50jtg/pqgSO+l7WFjK6/eaS/vJN00K7ab4MWay0IkNSKrzlVqgzDoUWePN/6tdFXu2wWZ/wNmBWwIDAQAB"
                        },
                        "ipAddress": "10.0.0.3",
                        "walletAddress": "0x85BBF9E273",
                        "walletBalance": 29.743273277327418
                      },
                      {
                        "name": "JAMES",
                        "keyPair": {
                          "privateKey": "MIICdwIBADANBgkqhkiG9w0BAQEFAASCAmEwggJdAgEAAoGBANeIf+fZxBnXwZoS6kubacBHHstl0h1W6Lt2jmKkc9mnHuIdwqMTX144YOFi34YNulMpnFbgmY1bOUlEqD3grAKlMJzzerhfCAJlErj8n3ipRmzbCBugxGSIig/OjXVJ8SEzrGr4U6WIOjhLOC+/cD49lseZ/SpUoGr8TjBjBelVAgMBAAECgYBmF4cWiIUmBJf05k7Kv3PpGwcQK0vKj/cvjOnG+cTSbSHo01X1ruB2ndfuCsp8EhaW+9fNIxg45+QLu5TBsqFmxNZ2MBhU8foxOhjmsSckPLFoPPr3lOdFWL8rj2mt7UdRHdXakomATVuSq/y0E/oLX7+06aAwDM2X9pYycQC6hQJBAOr8xFakUTQNTbp0zXLCxtE0qjCjVO+FGJtg4rT0SYubKCxiCbs/jCNzyNWpgA/pmsIQ8LazfzuJcKrFEtzbdB8CQQDqzmVciMb1SM7OTqWhpZATUOnkQBLzMl0jEJULPS1NbeSZr1ToGsv5ugpb9O1qDitD0PG4ORqCjwz0UpraypQLAkEA0NLPqC9d+mPYL1qdON7e0At9MrvzPueIdowi66wrr2Syzr5Q55YRc2/xie6XX4y5eryTLclVyBLbjOaVXK+pAQJAK533ej72Jm5F7FDzt5lXsB7hs8KrQn3iizbzWMkedzmos1u8e/kuVs9WSUqwJ3lGVCymGkCRShtknqYRmf7zjQJBAJSU8PrRE3vuh8noK3D26mRYt7XjlaaD6Z6HavuAC0ujrbqqZUHH6XKsWma7HMu5cKOoA8mdyMt77KbSlBJ+b28\u003d",
                          "publicKey": "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDXiH/n2cQZ18GaEupLm2nARx7LZdIdVui7do5ipHPZpx7iHcKjE19eOGDhYt+GDbpTKZxW4JmNWzlJRKg94KwCpTCc83q4XwgCZRK4/J94qUZs2wgboMRkiIoPzo11SfEhM6xq+FOliDo4Szgvv3A+PZbHmf0qVKBq/E4wYwXpVQIDAQAB"
                        },
                        "ipAddress": "10.0.0.4",
                        "walletAddress": "0xE728B64511",
                        "walletBalance": 19.4240628728881
                      },
                      {
                        "name": "JOHN",
                        "keyPair": {
                          "privateKey": "MIICdgIBADANBgkqhkiG9w0BAQEFAASCAmAwggJcAgEAAoGBAJERqBoiht41SPmKfkJBYjgZ+TmizSD2yWdzxWGvvzY/hKcnUIN61Fn7hVBuQw6UkeuUblqA39VUWTx0sVyS5+hbUWJix5T1N48vinwg+sKCM2JOT2IJtdsgBbv+dsf3hyI7Yy5L3HFTI+WtR8WWokDP1SJES8FU+Qep0owUHsxlAgMBAAECgYAGkwBFPu9RvqRx+p7CLehfln2+6OvW5Jg4Af0d8jL+KF7AnH1/l99rvIRMItMTJBSA0pc1rsV2C36HyNoI0feEdF+6CJjzd1DcdHbYOK1yacphNxQL74rPIre+e/sMwrAYlNsDTljRdAsAxLH7sgZLtOeNYHGbFXBMNSukQhA1eQJBAOrbYC6XIy2uuOQHIPtD0SR4WWgxOvUxzuTSIMI7fMwKB8Q5A+yUWPM20t0SeRGCJkWJ1TXGHGT5HFKH5g1cJJMCQQCeIPwkarT9HqoMeoFJbZUz8ZiMBWnUqsb4lT+MFq7VkHle5IF8D2Vb6/geaKM7V88aIbsK0TEsY8OQicrxhB4nAkEAzhEEWHzWKfwXUESNBMphVG2gjRI9F+zHCvDwO1x0yJa4b8xTDB6x2V0uMTlHLzySFgu1HsSgH1yQJD4HdYt2rwJAS5TekBWE7tuiUhaYB13ejBZ1YLARtrnuItFLr40EAkZtDONR1NeTDg3K6dX/95RwBECNI4174IjK6CJEo44PzwJAQ6qe71i3V1DU9tmjwxfdaiXureWSvwDjzhfGwOBY9h0RR86LmbQ7oVq1qILU20StaR6TT1gAvBcGOEFYqgnx/g\u003d\u003d",
                          "publicKey": "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCREagaIobeNUj5in5CQWI4Gfk5os0g9slnc8Vhr782P4SnJ1CDetRZ+4VQbkMOlJHrlG5agN/VVFk8dLFckufoW1FiYseU9TePL4p8IPrCgjNiTk9iCbXbIAW7/nbH94ciO2MuS9xxUyPlrUfFlqJAz9UiREvBVPkHqdKMFB7MZQIDAQAB"
                        },
                        "ipAddress": "10.0.0.5",
                        "walletAddress": "0xB678B02722",
                        "walletBalance": 17.900013567835465
                      },
                      {
                        "name": "ROBERT",
                        "keyPair": {
                          "privateKey": "MIICdwIBADANBgkqhkiG9w0BAQEFAASCAmEwggJdAgEAAoGBANldD+F0MZY0tzFbgNuFZ5SKYxpIPgfIpi9xKv+pqKHXdrNlQHcYmXFQE0eTNcV48t4WaJJ5S4+V2xW4h7ROuC9vApoxxQImjEhQNHIpKzyHralBFgbAqUFZoAm4PwJdLaXzmOLrqqy/1UIzMKNhQnOGfZ/JM2DdBZNAikwLxFjdAgMBAAECgYAw6EmcduJ5Y58ckfQqcJffykuGLF8YrUMHRbVhFTGGSM7CirO4mGZeIqBT6IGezxoGcpBQ9M3VnuhOuNh/735tc6oeOB8cE/EKh+EpHlKAig5UTKYdjtNKsljP/PlH2b2RQ4mVGQDnVNHaK9eyrBSu/MacgPKvrHA97hpIRTYfGQJBAO4y+kIMv5YXMFWc6roMNPQQzMPJXIXaOmBYW32vXQzJbAQi9+JJXVkencBuuGD89MGQbWhj1jSHGa2oBVUQqiMCQQDpm3m0QP0sRF8bVuU5qDLLf7lVo/xCgIfdi9Unr3iKYtrQi0K7gBFBQ2uCu0b7SGSrEO//rLSU+24t4EGDp6D/AkBPFp4BUDpMm2ZxBqjpHSR35RwX5cPSraK0WqIGGTPMCxTSSsoDWFimPoQiYKDXkyWxH0M0ZfG5fyIHhsI+fEoBAkEA2sStPu81qQUGhBXmaQ5pM0YTjG4byTORCRrwrU+YdRCKlo40Gl+eyR2YSz+TA7QSAlnESA22t2aXEgNXlzGehQJBAIY8M2Ejgzm2x72IJOqOYntegFPLM36bNoKy1E5YohZNWLybSoFz7kesEgvd2zWNGNmbrr7qgsfkC53KC3xdnNw\u003d",
                          "publicKey": "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDZXQ/hdDGWNLcxW4DbhWeUimMaSD4HyKYvcSr/qaih13azZUB3GJlxUBNHkzXFePLeFmiSeUuPldsVuIe0TrgvbwKaMcUCJoxIUDRyKSs8h62pQRYGwKlBWaAJuD8CXS2l85ji66qsv9VCMzCjYUJzhn2fyTNg3QWTQIpMC8RY3QIDAQAB"
                        },
                        "ipAddress": "10.0.0.6",
                        "walletAddress": "0x0A898B3D88",
                        "walletBalance": 16.72104728594938
                      },
                      {
                        "name": "MICHAEL",
                        "keyPair": {
                          "privateKey": "MIICdgIBADANBgkqhkiG9w0BAQEFAASCAmAwggJcAgEAAoGBAMPqoKVeq1Ngu1qGbOb0e0rYiI+e2YO3SHCG7hcBGqogZkbx6X0i/C2AG+0lqFixhIraXU615Fi6rliYiSUeoXq08mpxOBx/zXaaKMpx/aUDveHzNF6Z2GWa7KkiYd78uZnlFfvm7zMQojJBBLPiiMh33fZiVJbZEW121RaRvgBlAgMBAAECgYBo/y63UeQ53Casnkxw/mexNhkB950VNT4SaI3jMElNEe4eaXm8Aerqq7TLxJnTZtfk3qCRFXRahTn4607c4Oyi+ZYdC8qU2iL81ZUPancEqCJoyij27vTiJVuBkgiBeHixEyCpsh8DTPFi2lHta4btshZNPjLmwjwafigfRulnxQJBAOH+lkAFUeidKcu2pkebnduAqwVmXQqWWQCJXDMkgOGB7noEun7hBL/kCB1BnvogCDcMuD/2kP+YaZTl8EWyXIcCQQDd7bWFq2ZM7s/AI5OieaZPvkSxbVmwAANb+j/azxuzC+0iW4dBYGTg85PVuU1a9JUgQkXkndmdEo9v1TTAD8KzAkBdc0T2HD0Oj06lKlX/7l2MUtxlUzrOEfWjvykdDM8ucOgIFMR5itrH7qdcUbJHg89h1CSsbmzvBDtEkwCUEXkpAkBr6/MAI8/FzcRdjW937WyBDufc2G5q9jar/dAbmefAdCZHNPdbRYMJGapkno4Nky8J1vCiMljm12XFXosecwudAkEAiq9VwrKgxvCqKyeyZ4hG9i9kbKDbWcx3ow1b5kglWaiX+p6k7OkPOkH1tKzFKJhcaUlcaGzw0rbkIX3tGCPVVQ\u003d\u003d",
                          "publicKey": "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDD6qClXqtTYLtahmzm9HtK2IiPntmDt0hwhu4XARqqIGZG8el9IvwtgBvtJahYsYSK2l1OteRYuq5YmIklHqF6tPJqcTgcf812mijKcf2lA73h8zRemdhlmuypImHe/LmZ5RX75u8zEKIyQQSz4ojId932YlSW2RFtdtUWkb4AZQIDAQAB"
                        },
                        "ipAddress": "10.0.0.7",
                        "walletAddress": "0xFFDE325B6D",
                        "walletBalance": 26.150372200246075
                      },
                      {
                        "name": "MARY",
                        "keyPair": {
                          "privateKey": "MIICdgIBADANBgkqhkiG9w0BAQEFAASCAmAwggJcAgEAAoGBAJrNn2P5akd8nYPMCArsbB/anbkQbeQNc6WkGkRIq5GFSfUu/Y+W0v2tREVoPn8ZhqXnk50o+7mciQm6qKDdiSzernpijIFS4e7b6xTfnJujnJ4u9LGh63DIZnw6TUltCcLlJMb6CDndNUJT9gJMlZIDvhn5CfrJCEZJ9CxqBV/jAgMBAAECgYB/xiU/0cjFhLhG5wvkaEy+5iW1cTgjOm8wU4lSv2DN1mS7ObQU5vr0ZCWr3GVpZ1paahDPcdqE1A3Qt/8j+HeWARu3NYg7sz1wDBHHfjprv053cMdUy1z4N9W+IXkIugqyzlJi/6qsciOrhNksSfueOV0e2PXynm6NvyxEvir9oQJBAPB5oE3GXUtWAy9tLAH+zFhiTwXYkSL16Aw3JVC5bpRH6evxlLhbpzjjaXX93IDxNamBGCcMz4j8n6DbchGyjzkCQQCkzBUNO6cEp4Oz+r7jaz0DdCCdgcE1XEAWXiAMKDyf5OlQjmxaPMMeJxhnN9UQHy18kiwXMXvovoUUeh0ep4v7AkB03gckBSVN+Y0uvVXH2nag9ZYF90wBu5R8a0h9RMES2yg5/HwUZKaOJScZqrhBCfjUWeLNB5LMtkk1ubBkHW0xAkB8wVaKfwcwOdQ1UvRhW5SD4cyzEECAscZ+aGEgcvF6JiQObNNP+MHJONV7hkPQgZnAvTONl1NzXY+Hce5Lf5EzAkEA8FAnKZErrop7Oa5HHYGvUP5bETsBco+SSFL01nsViriiLmB48sXtQCp5Tx6XmdblIaqrupDgqED0moAvlKxAgg\u003d\u003d",
                          "publicKey": "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCazZ9j+WpHfJ2DzAgK7Gwf2p25EG3kDXOlpBpESKuRhUn1Lv2PltL9rURFaD5/GYal55OdKPu5nIkJuqig3Yks3q56YoyBUuHu2+sU35ybo5yeLvSxoetwyGZ8Ok1JbQnC5STG+gg53TVCU/YCTJWSA74Z+Qn6yQhGSfQsagVf4wIDAQAB"
                        },
                        "ipAddress": "10.0.0.8",
                        "walletAddress": "0xD6DB33A759",
                        "walletBalance": 25.500384575337442
                      },
                      {
                        "name": "WILLIAM",
                        "keyPair": {
                          "privateKey": "MIICdgIBADANBgkqhkiG9w0BAQEFAASCAmAwggJcAgEAAoGBAJ+zK4eTsHRjHVPgdzPwJ0HWVbvUQXgKKMlZ6E3QSpZUMN6TnuDDNXxlgRjCbQGpFYTNILQAoR1ZVyJcZymkv3aqPHob5tcl9B5wpW6o7EbDdJHnIT0SiWvrZ8nR8kXBhEyOpFO9DR/H1RK9LAt2MHdrP8FX3d9Q4SqgI7vWvmu3AgMBAAECgYArG/eFKpX4ZQT2rmIfMBW4zP3prRtbJwrph46Q6sgnmmRywNCjha57fP8DSwCX8Sew6JqKXJ0hSgueZ+klMabJGSgIZRJpqrjLN1r3lwoMbR7v/w2bLHIh8EOrkCbS7xjJkTytYlLoblKcJWCSTCQLLNBhFq5HOE1sz9xl92koUQJBANmb6qe0Gv65hDnhVVb9P67RNiNFn+fJ/KnW9jyD5fVB1N+YlnQZkQpqpJMk9PzEJ4AVOa4hZVhgHW2h1NX1Lb8CQQC739jRiVwnMXyei/fVvMsY7j4HLlbaeYkuhy1VSqyFuCXHlaKz0BRB0ks0C6LjFS4PNFepvtJ299be0hnidDAJAkAvoZIuXRzfjqnjpW/xl4M996s68z16FdtJLbU+L7zA/0TiIFsmVIZL2mXlk1xQi3r97Sdv9v1g58DpQ1f78SuvAkEAshZnZYBqXTCYq4WMQQv8DT7Qh2gSpngsZHF4tG/bh8nd7gnWt9IXUalw5f3dwpnyFjGMo3gh8unNwVuxc6qlWQJAPqRrk/X6RVYv/FHHUjTdVHhnIEt2CPQQw41hkj9/Al4ZtViV4VeTl/WhDbrLVORFmU6cZkWlhGKiI27oooktQA\u003d\u003d",
                          "publicKey": "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCfsyuHk7B0Yx1T4Hcz8CdB1lW71EF4CijJWehN0EqWVDDek57gwzV8ZYEYwm0BqRWEzSC0AKEdWVciXGcppL92qjx6G+bXJfQecKVuqOxGw3SR5yE9Eolr62fJ0fJFwYRMjqRTvQ0fx9USvSwLdjB3az/BV93fUOEqoCO71r5rtwIDAQAB"
                        },
                        "ipAddress": "10.0.0.9",
                        "walletAddress": "0xAC8A1DDF82",
                        "walletBalance": 17.974241366324012
                      },
                      {
                        "name": "DAVID",
                        "keyPair": {
                          "privateKey": "MIICdgIBADANBgkqhkiG9w0BAQEFAASCAmAwggJcAgEAAoGBAIcqeo0nWWs4HHmYLSsoD2knJa9kMRdJ9tiWs5wrHm+CAqPuCwng416Nfg1T36e624nZz/c7y8CAMbqf5qLezLvgpXkCebx6LfFub2nostBMshb0gp9p7L1Qjtn+vRds57Cetf4dq9I3qC/RVWZuW5W8GSW9a68EX82PAlzd5litAgMBAAECgYAxc3gctl2/nWrFjchQtrpUpU7jSUuwko3uFcymgRwEfdNLeGaveYiV6jxI0fvMmM+DMp4kfMsBpaUL+z5dLhhGxGwUtC/Kny+3Tswojxye7knxDG81Z06UlA2WdfqnMzi3MCr+tUKnQrksOPKltv5PMAQ2Yw6qAzcmxX41ousg/QJBAOurCZBMVCj6BzwPPCbm4zBXpBX2mCiwSlwnaRNYGkWul/9CyRuQ7BAw3RcWP4cJYqLKnbZ8Jf922aIKUa0ro+MCQQCS08Bn9Ex+Udu2otonbZWtuHy7E1ldh7N365rc7RMgqxbMYF/UnhzQ3HSPs1zpQ5d4CeKg3S5PPaRi9aPY51YvAkB2V8dPrOS+RwAHCud76YnApuIBHXm/RPeyWyAK1L0srMYrKtBuhVHlt4Puqf9wwifD89dK4gD6ziXvlxr4yOddAkBKVL0BY6IeCR7sFQHQGCBAdDdhFeiV+w8WVMZvte2LClJeYSPipbD84752yVzuEnPqJ0b+HTtGjnRcTPMH6gWtAkEAyxUKS4iHl4VQXBcwElFGw/sOEQuWdpEw5RNbpjysoemaMyvZ8v/I1lNmkFvXQM+vAcFTkbedhuPxjPhwKKWFXA\u003d\u003d",
                          "publicKey": "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCHKnqNJ1lrOBx5mC0rKA9pJyWvZDEXSfbYlrOcKx5vggKj7gsJ4ONejX4NU9+nutuJ2c/3O8vAgDG6n+ai3sy74KV5Anm8ei3xbm9p6LLQTLIW9IKfaey9UI7Z/r0XbOewnrX+HavSN6gv0VVmbluVvBklvWuvBF/NjwJc3eZYrQIDAQAB"
                        },
                        "ipAddress": "10.0.0.10",
                        "walletAddress": "0xB94E03B082",
                        "walletBalance": 48.88033449135272
                      }
                    ],
                    "channels": [
                      {
                        "fromWallet": "0x0A898B3D88",
                        "toWallet": "0x85BBF9E273",
                        "fromBalance": 5.454782068152315,
                        "toBalance": 6.649223259929028
                      },
                      {
                        "fromWallet": "0x85BBF9E273",
                        "toWallet": "0xB678B02722",
                        "fromBalance": 3.852029869542835,
                        "toBalance": 4.267454305394121
                      },
                      {
                        "fromWallet": "0xAC8A1DDF82",
                        "toWallet": "0xFFDE325B6D",
                        "fromBalance": 3.6809794409594274,
                        "toBalance": 4.147921070153279
                      },
                      {
                        "fromWallet": "0xB94E03B082",
                        "toWallet": "0x045AC964A9",
                        "fromBalance": 1.996246688494809,
                        "toBalance": 1.363724765664104
                      },
                      {
                        "fromWallet": "0x1E3D661B65",
                        "toWallet": "0x85BBF9E273",
                        "fromBalance": 1.5944328430926078,
                        "toBalance": 2.2899385894803164
                      },
                      {
                        "fromWallet": "0xB678B02722",
                        "toWallet": "0xAC8A1DDF82",
                        "fromBalance": 7.077527440242866,
                        "toBalance": 2.314010550487806
                      },
                      {
                        "fromWallet": "0x1E3D661B65",
                        "toWallet": "0x0A898B3D88",
                        "fromBalance": 1.385437376069801,
                        "toBalance": 2.8155802398630643
                      },
                      {
                        "fromWallet": "0xB94E03B082",
                        "toWallet": "0xE728B64511",
                        "fromBalance": 2.2987981809055755,
                        "toBalance": 2.537202122413491
                      },
                      {
                        "fromWallet": "0xE728B64511",
                        "toWallet": "0x0A898B3D88",
                        "fromBalance": 5.36963193051923,
                        "toBalance": 1.9861383066855256
                      },
                      {
                        "fromWallet": "0xB94E03B082",
                        "toWallet": "0xB678B02722",
                        "fromBalance": 1.9076803402012878,
                        "toBalance": 3.2571292941110475
                      },
                      {
                        "fromWallet": "0x0A898B3D88",
                        "toWallet": "0x045AC964A9",
                        "fromBalance": 4.658679031826976,
                        "toBalance": 6.079596351241962
                      },
                      {
                        "fromWallet": "0xAC8A1DDF82",
                        "toWallet": "0xB94E03B082",
                        "fromBalance": 6.920874640504412,
                        "toBalance": 5.205715328769639
                      },
                      {
                        "fromWallet": "0xAC8A1DDF82",
                        "toWallet": "0xD6DB33A759",
                        "fromBalance": 5.288474614754871,
                        "toBalance": 4.727212529435874
                      },
                      {
                        "fromWallet": "0xFFDE325B6D",
                        "toWallet": "0xD6DB33A759",
                        "fromBalance": 4.122273951151044,
                        "toBalance": 9.111120965581188
                      }
                    ]
                }
            """.trimIndent()

    @Before
    fun setup() {
        val gsonBuilder = GsonBuilder()
        gsonBuilder.registerTypeAdapter(KeyPair::class.java, PCNSimulationCLI.KeyPairTypeAdapter)
        val gson = gsonBuilder.create()
        val template: NetworkTemplate = gson.fromJson<NetworkTemplate>(templateJSON, NetworkTemplate::class.java)

        communication = SyncIPNetwork()
        blockchain = MockBlockchain

        // simulation build
        val templateNodes = template.nodes.map { Pair(it.walletAddress, it) }.toMap()

        channels = template.channels.map {
            val fromNode = templateNodes[it.fromWallet]!!
            val toNode = templateNodes[it.toWallet]!!
            TransactionChannel(
                    FundingTransaction(
                            fromWallet = it.fromWallet,
                            toWallet = it.toWallet,
                            inputs = emptyList(),
                            outputs = listOf(UnconditionalOutput(it.fromWallet, it.fromBalance), UnconditionalOutput(it.toWallet, it.toBalance)),
                            fromPublicKey = fromNode.keyPair.public,
                            toPublicKey = toNode.keyPair.public,
                            cycle = 0
                    )
            )
        }
        val nodeChannels = channels
                .flatMap { listOf(Pair(it.fromWallet, it), Pair(it.toWallet, it)) }
                .groupBy { it.first }
                .mapValues { it.value.map { e -> e.second }.toSet() }

        nodes = template.nodes.map {
            val socket = communication.createSocket(it.ipAddress)
            val peer = Peer(it.name, it.keyPair.public, it.walletAddress, it.ipAddress)
            val chs = nodeChannels.getOrDefault(it.walletAddress, emptySet())
            val routerChannels = chs.map { c -> StaticChannelInformation(c.fromWallet, c.fromBalance(), c.toWallet, c.toBalance()) }.toSet()
            val routerPeers = chs.map { c ->
                val n = templateNodes.getValue(c.otherWallet(it.walletAddress))
                Pair(n.walletAddress, Peer(n.name, n.keyPair.public, n.walletAddress, n.ipAddress))
            }.toMap()
            val strategy = Strategy()
            val mdartAddrSize = (log2(template.nodes.size.toDouble()) + 1.5).roundToInt()
            val router = MDARTRouter(peer, socket, strategy, routerPeers, routerChannels, mdartAddrSize, 5, mdartAddrSize * 2, 30)
            val node = BasicNode(it.name, it.keyPair, it.walletAddress, blockchain, strategy, chs, socket, router)
            Pair(it.walletAddress, node)
        }.toMap()
    }

    @Test
    fun `Cycle nodes and validate loop avoidance`() = runBlocking {
        for (i in 0..19) {
            println("### Cycle $i ###")
            nodes.values.forEach {
                it.cycle(i)
                print(it.router.toString())
            }
        }
        Assert.assertEquals("All routers must have an address.", 0, nodes.count {
            val f = MDARTRouter::class.java.getDeclaredField("state")
            f.isAccessible = true
            (f.get(it.value.router) as MDARTRouter.NodeState).address < 0
        })

    }
}
